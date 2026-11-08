package io.github.lawrencekcleung.pft.store;

import io.github.lawrencekcleung.pft.error.StorageException;
import io.github.lawrencekcleung.pft.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CSV-backed {@link TransactionRepository}.
 *
 * <p>The whole file is read on each operation and rewritten through a temporary file, moved into
 * place with {@link StandardCopyOption#ATOMIC_MOVE}. A crash mid-write therefore leaves the previous
 * file intact; no partially written state is ever observable.</p>
 *
 * <p>A missing file or directory means an empty store. Directories are created on the first
 * successful write. Ordering is the file order (id order, since ids are only appended).</p>
 *
 * <p>Ids are never reused: the next id is stored in a small sidecar file
 * ({@code transactions.csv.seq}) and is written <em>before</em> the data file. A crash between the
 * two writes can therefore only skip an id, never hand it out twice. When the sidecar is missing
 * the data file's highest id is used as a fallback high-water mark.</p>
 */
public final class CsvTransactionRepository implements TransactionRepository {

    /**
     * Suffix of the high-water-mark file next to the data file. It records the next id to assign so
     * that deleting the highest id cannot cause that id to be reused. The interface only promises
     * uniqueness and monotonicity; this file is the CSV adapter's private implementation detail.
     */
    private static final String COUNTER_SUFFIX = ".seq";

    private final Path path;
    private final CsvCodec codec;

    /**
     * Creates a repository for the given data file.
     *
     * @param path the CSV file; it need not exist yet
     */
    public CsvTransactionRepository(Path path) {
        this.path = Objects.requireNonNull(path, "path is required");
        this.codec = new CsvCodec();
    }

    @Override
    public List<Transaction> findAll() {
        return readAll();
    }

    @Override
    public Optional<Transaction> findById(int id) {
        return readAll().stream()
                .filter(tx -> tx.id() == id)
                .findFirst();
    }

    @Override
    public Transaction insert(Transaction draft) {
        List<Transaction> current = new ArrayList<>(readAll());
        int id = nextId(current);
        Transaction stored = new Transaction(id, draft.date(), draft.type(),
                draft.amount(), draft.category(), draft.description());
        // Claim the id before touching the data file: a failed data write then leaks an id
        // instead of allowing it to be assigned twice.
        writeCounter(id + 1);
        current.add(stored);
        writeAll(current);
        return stored;
    }

    @Override
    public List<Transaction> insertAll(List<Transaction> drafts) {
        Objects.requireNonNull(drafts, "drafts is required");
        if (drafts.isEmpty()) {
            return List.of();
        }
        List<Transaction> current = new ArrayList<>(readAll());
        int id = nextId(current);
        List<Transaction> stored = new ArrayList<>(drafts.size());
        // Ids are claimed in memory first: a null draft fails here, before either file is touched.
        for (Transaction draft : drafts) {
            Objects.requireNonNull(draft, "draft is required");
            stored.add(new Transaction(id++, draft.date(), draft.type(),
                    draft.amount(), draft.category(), draft.description()));
        }
        current.addAll(stored);
        writeCounter(id);
        writeAll(current);
        return List.copyOf(stored);
    }

    @Override
    public void update(Transaction tx) {
        List<Transaction> current = new ArrayList<>(readAll());
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).id() == tx.id()) {
                current.set(i, tx);
                writeAll(current);
                return;
            }
        }
        throw new StorageException("unknown transaction id: " + tx.id());
    }

    @Override
    public void deleteById(int id) {
        List<Transaction> current = new ArrayList<>(readAll());
        boolean removed = current.removeIf(tx -> tx.id() == id);
        if (!removed) {
            throw new StorageException("unknown transaction id: " + id);
        }
        writeAll(current);
    }

    /**
     * Returns the next id to assign: one above both the highest id in the data file and the
     * sidecar high-water mark, so a deleted id is never handed out again.
     */
    private int nextId(List<Transaction> transactions) {
        int fromData = transactions.stream()
                .mapToInt(Transaction::id)
                .max()
                .orElse(0) + 1;
        return Math.max(fromData, readCounter());
    }

    private Path counterPath() {
        return path.resolveSibling(path.getFileName() + COUNTER_SUFFIX);
    }

    /**
     * Reads the next id from the sidecar file. A missing file means the counter was never written
     * or was deleted; the data file then provides the high-water mark on its own.
     */
    private int readCounter() {
        Path counter = counterPath();
        if (!Files.exists(counter)) {
            return 1;
        }
        String raw;
        try {
            raw = Files.readString(counter, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new StorageException("cannot read " + counter + ": " + e.getMessage(), e);
        }
        try {
            int next = Integer.parseInt(raw);
            if (next < 1) {
                throw new NumberFormatException("not positive");
            }
            return next;
        } catch (NumberFormatException e) {
            throw new StorageException(counter + ": invalid counter '" + raw + "'");
        }
    }

    private void writeCounter(int nextId) {
        writeAtomically(counterPath(), Integer.toString(nextId) + "\n");
    }

    private List<Transaction> readAll() {
        if (!Files.exists(path)) {
            return List.of();
        }
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("cannot read " + path + ": " + e.getMessage(), e);
        }
        try {
            return codec.parse(content);
        } catch (StorageException e) {
            // Codec messages are "N: detail"; the PRD wants "path:N: detail".
            throw new StorageException(path + ":" + e.getMessage(), e);
        }
    }

    private void writeAll(List<Transaction> transactions) {
        StringBuilder content = new StringBuilder(codec.header()).append('\n');
        for (Transaction tx : transactions) {
            content.append(codec.encode(tx)).append('\n');
        }
        writeAtomically(path, content.toString());
    }

    /**
     * Writes {@code content} to {@code target} through {@code <target>.tmp}, then moves it into
     * place atomically where the file system supports it.
     */
    private static void writeAtomically(Path target, String content) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Path directory = target.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Files.writeString(temp, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("cannot write " + target + ": " + e.getMessage(), e);
        }
    }
}
