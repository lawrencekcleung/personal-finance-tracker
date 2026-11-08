package io.github.lawrencekcleung.pft.service;

import io.github.lawrencekcleung.pft.model.Money;
import io.github.lawrencekcleung.pft.model.Transaction;
import io.github.lawrencekcleung.pft.model.TransactionType;
import io.github.lawrencekcleung.pft.store.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryBreakdownTest {

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

    private List<String> categories(Summary summary) {
        return summary.categories().stream().map(CategorySpend::category).toList();
    }

    @Test
    void ordersBySpendDescendingThenCategoryName() {
        addNovemberFixture();

        assertEquals(List.of("groceries", "client-entertainment", "dining",
                "transport", "subscriptions"),
                categories(service.summarize(YearMonth.of(2026, 11))));
    }

    @Test
    void equalSpendIsTieBrokenByName() {
        add("2026-11-01", "expense", "10.00", "zebra");
        add("2026-11-02", "expense", "10.00", "apple");

        assertEquals(List.of("apple", "zebra"),
                categories(service.summarize(YearMonth.of(2026, 11))));
    }

    @Test
    void percentagesUseBasisPointsAndRoundAtTheDisplayEdge() {
        addNovemberFixture();

        Summary summary = service.summarize(YearMonth.of(2026, 11));

        assertEquals(List.of("60.1%", "14.1%", "10.7%", "8.6%", "6.5%"),
                summary.categories().stream().map(CategorySpend::share).toList());
    }

    @Test
    void basisPointsAreIntegerTruncations() {
        addNovemberFixture();

        // 110.90 / 184.60 = 60.075...%, truncated to 6007 basis points.
        assertEquals(6007, service.summarize(YearMonth.of(2026, 11)).categories().get(0)
                .basisPoints());
    }

    @Test
    void incomeIsNotACategoryRow() {
        addNovemberFixture();

        assertTrue(categories(service.summarize(YearMonth.of(2026, 11))).stream()
                .noneMatch("income"::equals));
    }

    @Test
    void zeroExpensesDoNotDivideByZero() {
        add("2026-11-01", "income", "5000.00", "income");

        Summary summary = service.summarize(YearMonth.of(2026, 11));

        assertTrue(summary.categories().isEmpty());
        assertEquals(Money.ZERO, summary.expenses());
    }

    @Test
    void aSingleCategoryIsOneHundredPercent() {
        add("2026-11-01", "expense", "42.50", "groceries");

        assertEquals("100.0%",
                service.summarize(YearMonth.of(2026, 11)).categories().get(0).share());
    }
}
