package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.store.TransactionRepository;

import java.util.Objects;

/**
 * Imports a CSV file into the store.
 *
 * <p>Valid rows are committed in a single {@link TransactionRepository#insertAll} call, so a bad
 * row neither aborts the good ones nor consumes an id: ids stay gapless. Skipped rows are
 * reported with their source line and the caller decides the exit code.</p>
 */
public final class ImportService {

    private final TransactionRepository repository;
    private final ImportParser parser;

    /**
     * Creates the service over a repository.
     *
     * @param repository the store to write to
     */
    public ImportService(TransactionRepository repository) {
        this(repository, new ImportParser());
    }

    ImportService(TransactionRepository repository, ImportParser parser) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.parser = Objects.requireNonNull(parser, "parser is required");
    }

    /**
     * Parses and stores the file content.
     *
     * @param content the import file content
     * @return the stored count and the skipped rows
     */
    public ImportReport importCsv(String content) {
        ParsedImport parsed = parser.parse(content);
        if (!parsed.drafts().isEmpty()) {
            repository.insertAll(parsed.drafts());
        }
        return new ImportReport(parsed.drafts().size(), parsed.errors());
    }
}
