package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryServiceTest {

    private InMemoryTransactionRepository repository;
    private SummaryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        service = new SummaryService(repository);
    }

    private void add(String date, String type, String amount, String category) {
        repository.insert(new Transaction(0, LocalDate.parse(date),
                TransactionType.fromWire(type), Money.parse(amount), category, "Transaction"));
    }

    private void addNovemberFixture() {
        add("2026-11-01", "income", "5000.00", "income");
        add("2026-11-01", "expense", "42.50", "groceries");
        add("2026-11-02", "expense", "15.80", "transport");
        add("2026-11-03", "expense", "68.40", "groceries");
        add("2026-11-04", "expense", "12.00", "subscriptions");
        add("2026-11-06", "expense", "26.10", "client-entertainment");
        add("2026-11-06", "expense", "9.90", "dining");
        add("2026-11-07", "expense", "9.90", "dining");
    }

    @Test
    void totalsMatchTheHandCheckedFixture() {
        addNovemberFixture();

        Summary summary = service.summarize(YearMonth.of(2026, 11));

        assertEquals(Money.ofCents(500000), summary.income());
        assertEquals(Money.ofCents(18460), summary.expenses());
        assertEquals(Money.ofCents(481540), summary.net());
        assertEquals(8, summary.transactionCount());
        assertFalse(summary.isEmpty());
    }

    @Test
    void monthFilterExcludesOtherMonths() {
        addNovemberFixture();
        add("2026-12-01", "expense", "100.00", "dining");

        assertEquals(Money.ofCents(18460),
                service.summarize(YearMonth.of(2026, 11)).expenses());
        assertEquals(Money.ofCents(28460),
                service.summarize(null).expenses());
    }

    @Test
    void anEmptyMonthHasZeroTotalsAndNoCategories() {
        addNovemberFixture();

        Summary summary = service.summarize(YearMonth.of(2026, 12));

        assertTrue(summary.isEmpty());
        assertEquals(Money.ZERO, summary.income());
        assertEquals(Money.ZERO, summary.expenses());
        assertEquals(Money.ZERO, summary.net());
        assertTrue(summary.categories().isEmpty());
    }

    @Test
    void theNetCanBeNegative() {
        add("2026-11-01", "income", "100.00", "income");
        add("2026-11-02", "expense", "150.00", "dining");

        Summary summary = service.summarize(YearMonth.of(2026, 11));

        assertEquals(Money.ofCents(-5000), summary.net());
        assertEquals("-50.00", summary.net().formatSigned());
    }

    @Test
    void incomeOnlyHasNoCategoryRows() {
        add("2026-11-01", "income", "5000.00", "income");

        Summary summary = service.summarize(YearMonth.of(2026, 11));

        assertTrue(summary.categories().isEmpty());
        assertEquals(Money.ofCents(500000), summary.income());
        assertEquals(Money.ZERO, summary.expenses());
    }

    @Test
    void summarisingDoesNotWrite() {
        addNovemberFixture();
        int before = repository.writeCount();

        service.summarize(YearMonth.of(2026, 11));

        assertEquals(before, repository.writeCount());
    }
}
