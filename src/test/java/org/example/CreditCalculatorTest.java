package org.example;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditCalculatorTest {

    private final CreditCalculator calculator = new CreditCalculator();

    @Test
    void dailyInterestAccrual_firstMonth_correctAmount() {
        // 1000 EUR in Jan 2025, 2-month repayment.
        // Feb has 28 days. Daily interest = 1000 * 0.14 / 365 per day.
        // Expected Feb interest = 28 * (1000 * 0.14 / 365) = 10.74 EUR (rounded)
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                YearMonth.of(2025, 1),
                2
        );

        assertEquals(2, schedule.size());
        MonthlyStatement feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1000.00"), feb.openingBalance());
        assertEquals(new BigDecimal("10.74"), feb.interestCharged());
    }

    @Test
    void paymentAllocation_interestPaidFirst_thenPrincipal() {
        // 1000 EUR in Jan 2025, 2-month repayment.
        // Feb interest = 10.74. Equal payment covers interest first, remainder reduces principal.
        // After Feb payment: closing = opening + interest - payment
        // Month 2 opening balance must equal month 1 closing balance.
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                YearMonth.of(2025, 1),
                2
        );

        MonthlyStatement feb = schedule.get(0);
        MonthlyStatement mar = schedule.get(1);

        // Payment must exceed interest to reduce principal
        assertTrue(feb.paymentAmount().compareTo(feb.interestCharged()) > 0,
                "Payment must exceed interest charged");

        // Month 2 opening = month 1 closing
        assertEquals(feb.closingBalance(), mar.openingBalance(),
                "Month 2 opening must equal month 1 closing");

        // Final balance = 0
        assertEquals(0, mar.closingBalance().compareTo(BigDecimal.ZERO),
                "Final closing balance must be zero");
    }

    @Test
    void interestCapitalization_unpaidInterestAddsToPrincipal() {
        // 1000 EUR in Jan, 6 months. Equal payments are small relative to balance.
        // After month 1: closing balance = opening + interest - payment
        // Month 2 interest calculated on closing balance (which includes any capitalized interest)
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                YearMonth.of(2025, 1),
                6
        );

        MonthlyStatement feb = schedule.get(0);
        MonthlyStatement mar = schedule.get(1);

        // Feb closing = 1000 + 10.74 - payment
        BigDecimal expectedFebClosing = new BigDecimal("1000.00")
                .add(feb.interestCharged())
                .subtract(feb.paymentAmount());
        assertEquals(0, expectedFebClosing.compareTo(feb.closingBalance()),
                "Feb closing = opening + interest - payment");

        // Mar opening = Feb closing (which includes capitalized interest portion)
        assertEquals(feb.closingBalance(), mar.openingBalance());

        // Mar interest calculated on higher base (includes unpaid interest from Feb)
        // If payment < opening + interest, some interest capitalized
        // Mar daily interest based on Feb closing balance
        // Mar has 31 days: expected = closingFeb * 0.14 / 365 * 31
        BigDecimal expectedMarInterest = feb.closingBalance()
                .multiply(new BigDecimal("0.14"))
                .multiply(new BigDecimal("31"))
                .divide(new BigDecimal("365"), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedMarInterest, mar.interestCharged(),
                "Mar interest based on Feb closing balance (includes capitalized interest)");
    }

    @Test
    void minimumPayment_fivePercentOrFiveEur_whicheverHigher() {
        // 5% of 1000 = 50 EUR. 5% of 80 = 4.00, floor = 5.00
        assertEquals(new BigDecimal("50.00"), calculator.calculateMinimumPayment(new BigDecimal("1000.00")));
        assertEquals(new BigDecimal("5.00"), calculator.calculateMinimumPayment(new BigDecimal("80.00")));
        assertEquals(new BigDecimal("5.00"), calculator.calculateMinimumPayment(new BigDecimal("50.00")));
        assertEquals(new BigDecimal("5.25"), calculator.calculateMinimumPayment(new BigDecimal("105.00")));
    }

    @Test
    void minimumPayment_scheduleNeverBelowMinimum() {
        // 1000 EUR over 6 months. Each monthly payment must >= minimum payment for that month.
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                YearMonth.of(2025, 1),
                6
        );

        for (MonthlyStatement statement : schedule) {
            assertTrue(statement.paymentAmount().compareTo(statement.minimumPayment()) >= 0,
                    "Payment " + statement.paymentAmount() +
                            " must be >= minimum " + statement.minimumPayment() +
                            " in " + statement.month());
        }
    }

    @Test
    void tooManyMonths_paymentBelowMinimum_throwsException() {
        // 100 EUR over 100 months: equal payment ~1 EUR, well below 5 EUR minimum.
        assertThrows(IllegalArgumentException.class, () ->
                calculator.calculatePaymentSchedule(
                        new BigDecimal("100.00"),
                        YearMonth.of(2025, 1),
                        100
                ));
    }

    @Test
    void equalPayments_allMonthsExceptLastHaveSameAmount() {
        // 1500 EUR in Jan, 6 months. First 5 months should have equal payment.
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                YearMonth.of(2025, 1),
                6
        );

        assertEquals(6, schedule.size());

        BigDecimal firstPayment = schedule.getFirst().paymentAmount();
        for (int i = 0; i < 5; i++) {
            assertEquals(firstPayment, schedule.get(i).paymentAmount(),
                    "Month " + (i + 1) + " payment should equal first payment");
        }
        // Last month clears remainder, may differ
    }

    @Test
    void multiMonthSchedule_finalBalanceZero() {
        // Various scenarios: balance must reach zero after last payment
        for (int months : new int[]{2, 3, 6, 12}) {
            List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                    new BigDecimal("1500.00"),
                    YearMonth.of(2025, 1),
                    months
            );

            assertEquals(months, schedule.size());
            assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO),
                    "Final balance must be zero for " + months + "-month schedule");
        }
    }

    @Test
    void multiMonthSchedule_consecutiveMonthsLinked() {
        // Each month's opening balance = previous month's closing balance
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                YearMonth.of(2025, 1),
                6
        );

        for (int i = 1; i < schedule.size(); i++) {
            assertEquals(schedule.get(i - 1).closingBalance(), schedule.get(i).openingBalance(),
                    "Month " + (i + 1) + " opening must equal month " + i + " closing");
        }
    }

    @Test
    void totalInterest_multiMonth_greaterThanZero() {
        // Over 6 months, total interest paid must be positive
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                YearMonth.of(2025, 1),
                6
        );

        BigDecimal totalInterest = schedule.stream()
                .map(MonthlyStatement::interestCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalInterest.compareTo(BigDecimal.ZERO) > 0, "Total interest must be positive");

        BigDecimal totalPaid = schedule.stream()
                .map(MonthlyStatement::paymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total paid = spend + total interest
        BigDecimal expectedTotalPaid = new BigDecimal("1500.00").add(totalInterest);
        assertEquals(0, expectedTotalPaid.compareTo(totalPaid),
                "Total paid must equal spend + total interest");
    }

    @Test
    void gracePeriod_payFullInOneMonth_zeroInterest() {
        // Spend 1000 EUR in January, repay in 1 month (by end of February)
        // Grace period applies: no interest charged
        List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                YearMonth.of(2025, 1),
                1
        );

        assertEquals(1, schedule.size());

        MonthlyStatement feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1000.00"), feb.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.interestCharged());
        assertEquals(new BigDecimal("1000.00"), feb.paymentAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.closingBalance());
    }
}
