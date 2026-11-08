package io.github.lawrencekcleung.pft.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Money}: cents semantics, formatting, arithmetic and ordering.
 */
class MoneyTest {

    @Test
    void equalityIsBasedOnCents() {
        assertEquals(Money.ofCents(100), Money.ofCents(100));
        assertEquals(Money.ofCents(100).hashCode(), Money.ofCents(100).hashCode());
        assertEquals(100L, Money.ofCents(100).cents());
    }

    @Test
    void formatRendersTwoDecimalPlaces() {
        assertEquals("0.00", Money.ofCents(0).format());
        assertEquals("42.50", Money.ofCents(4250).format());
        assertEquals("5000000.00", Money.ofCents(500_000_000).format());
        assertEquals("-1234.56", Money.ofCents(-123456).format());
    }

    @Test
    void formatSignedAddsPlusForPositives() {
        assertEquals("+3157.70", Money.ofCents(315_770).formatSigned());
        assertEquals("-120.00", Money.ofCents(-12_000).formatSigned());
        assertEquals("0.00", Money.ZERO.formatSigned());
    }

    @Test
    void plusAddsCents() {
        assertEquals(Money.ofCents(4250), Money.ofCents(4200).plus(Money.ofCents(50)));
    }

    @Test
    void minusCanProduceNegativeNetTotals() {
        Money income = Money.ofCents(10_000);
        Money expense = Money.ofCents(13_157);
        Money net = income.minus(expense);
        assertEquals(Money.ofCents(-3157), net);
        assertEquals("-31.57", net.format());
    }

    @Test
    void negateFlipsTheSign() {
        assertEquals(Money.ofCents(-500), Money.ofCents(500).negate());
        assertEquals(Money.ofCents(500), Money.ofCents(-500).negate());
        assertEquals(Money.ZERO, Money.ZERO.negate());
    }

    @Test
    void isZeroAndIsNegativeReportTheSign() {
        assertTrue(Money.ZERO.isZero());
        assertFalse(Money.ZERO.isNegative());
        assertFalse(Money.ofCents(1).isNegative());
        assertTrue(Money.ofCents(-1).isNegative());
        assertFalse(Money.ofCents(-1).isZero());
    }

    @Test
    void compareToOrdersNegativeBeforeZeroBeforePositive() {
        Money negative = Money.ofCents(-100);
        Money zero = Money.ZERO;
        Money positive = Money.ofCents(100);

        assertTrue(negative.compareTo(zero) < 0);
        assertTrue(zero.compareTo(negative) > 0);
        assertTrue(zero.compareTo(positive) < 0);
        assertTrue(positive.compareTo(zero) > 0);
        assertEquals(0, positive.compareTo(Money.ofCents(100)));
    }

    @Test
    void toStringMatchesFormat() {
        assertEquals(Money.ofCents(4250).format(), Money.ofCents(4250).toString());
    }

    @Test
    void plusOverflowThrows() {
        Money max = new Money(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> max.plus(Money.ofCents(1)));
    }
}
