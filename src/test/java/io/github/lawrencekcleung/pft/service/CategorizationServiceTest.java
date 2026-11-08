package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.error.DataException;
import io.github.lawrencekcleung.pft.model.Category;
import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.rules.KeywordCategorizationRule;
import io.github.lawrencekcleung.pft.rules.RuleRegistry;
import io.github.lawrencekcleung.pft.store.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorizationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);

    private InMemoryTransactionRepository repository;
    private CategorizationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        service = new CategorizationService(repository,
                RuleRegistry.of(List.of(new KeywordCategorizationRule()), List.of()));
    }

    private Transaction add(String description, String category) {
        return repository.insert(new Transaction(0, DATE, TransactionType.EXPENSE,
                Money.ofCents(100), category, description));
    }

    // ------------------------------------------------------------------- plan

    @Test
    void planOnlyProposesChangesForUncategorizedRows() {
        Transaction uncategorized = add("Countdown", null);
        add("Countdown", "groceries");

        List<CategoryChange> plan = service.plan();

        assertEquals(1, plan.size());
        CategoryChange change = plan.get(0);
        assertEquals(uncategorized, change.transaction());
        assertEquals(Category.UNCATEGORIZED, change.from());
        assertEquals("groceries", change.to());
    }

    @Test
    void planSkipsRowsNoRuleRecognises() {
        add("Mystery shop", null);

        assertTrue(service.plan().isEmpty());
    }

    @Test
    void planIsOrderedById() {
        add("Countdown", null);
        add("Uber ride", null);
        add("Spotify", null);

        assertEquals(List.of(1, 2, 3),
                service.plan().stream().map(change -> change.transaction().id()).toList());
    }

    @Test
    void planningWritesNothing() {
        add("Countdown", null);
        int before = repository.writeCount();

        service.plan();

        assertEquals(before, repository.writeCount());
    }

    // ------------------------------------------------------------------ apply

    @Test
    void applyUpdatesEveryPlannedRow() {
        Transaction first = add("Countdown", null);
        Transaction second = add("Uber ride", null);
        List<CategoryChange> plan = service.plan();
        int before = repository.writeCount();

        assertEquals(2, service.apply(plan));

        assertEquals("groceries", repository.findById(first.id()).orElseThrow().category());
        assertEquals("transport", repository.findById(second.id()).orElseThrow().category());
        assertEquals(before + 2, repository.writeCount());
    }

    @Test
    void applyWithAnEmptyPlanWritesNothing() {
        add("Mystery shop", null);
        int before = repository.writeCount();

        assertEquals(0, service.apply(service.plan()));
        assertEquals(before, repository.writeCount());
    }

    @Test
    void rePlanningAfterApplyIsEmpty() {
        add("Countdown", null);
        service.apply(service.plan());

        assertTrue(service.plan().isEmpty());
    }

    // --------------------------------------------------------------- override

    @Test
    void overrideAssignsTheCategoryAndKeepsEverythingElse() {
        Transaction original = add("Mystery shop", null);
        int before = repository.writeCount();

        Transaction updated = service.override(original.id(), "client-entertainment");

        assertEquals("client-entertainment", updated.category());
        assertEquals(original.date(), updated.date());
        assertEquals(original.amount(), updated.amount());
        assertEquals(original.description(), updated.description());
        assertEquals(before + 1, repository.writeCount());
        assertEquals(updated, repository.findById(original.id()).orElseThrow());
    }

    @Test
    void overrideWinsOverALaterPlan() {
        Transaction overridden = add("Countdown", null);
        service.override(overridden.id(), "dining");

        assertTrue(service.plan().isEmpty());
    }

    @Test
    void unknownIdIsADataExceptionAndWritesNothing() {
        int before = repository.writeCount();

        DataException e = assertThrows(DataException.class, () -> service.override(99, "dining"));

        assertEquals("unknown transaction id: 99", e.getMessage());
        assertEquals(before, repository.writeCount());
    }

    @Test
    void blankCategoryIsADataException() {
        Transaction stored = add("Mystery", null);
        int before = repository.writeCount();

        DataException e = assertThrows(DataException.class,
                () -> service.override(stored.id(), "   "));

        assertEquals("category is required", e.getMessage());
        assertEquals(before, repository.writeCount());
    }
}
