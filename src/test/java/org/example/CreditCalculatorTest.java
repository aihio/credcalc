package org.example;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class CreditCalculatorTest {

    private final CreditCalculator calculator = new CreditCalculator();

    // --- Interest accrual ---

    @Test
    void dailyInterestAccrual_includesPreBillingAndFirstMonthInterest() {
        // 1000 EUR on Jan 1 2025, 3-month repayment.
        // Pre-billing: Jan 1-31 = 31 days at 0.14/365.
        // Opening balance of Feb = 1000 + pre-billing interest = 1011.89.
        // Feb interest (28 days on 1011.89) = 10.87.
        // Displayed first-month interest = pre-billing (11.89) + Feb interest (10.87) = 22.76.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                3
        );

        assertEquals(3, schedule.size());
        var feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1011.89"), feb.openingBalance());
        assertEquals(new BigDecimal("22.76"), feb.interestCharged());
    }

    @Test
    void midMonthPurchase_preBillingInterestReduced() {
        // 1000 EUR on Jan 15 2025, 3 months.
        // Pre-billing: Jan 15-31 = 17 days. Less interest than full-month purchase.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 15),
                3
        );

        assertEquals(3, schedule.size());
        var feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1006.52"), feb.openingBalance());
        assertEquals(new BigDecimal("17.33"), feb.interestCharged());

        // Less interest than Jan 1 purchase (opening 1011.89)
        assertTrue(feb.openingBalance().compareTo(new BigDecimal("1011.89")) < 0,
                "Mid-month purchase should have less pre-billing interest");
    }

    @Test
    void leapYear_dailyRateUses366Days() {
        // 1000 EUR on Feb 1 2024 (leap year), 3 months.
        // Daily rate = 0.14/366. Pre-billing: 29 days in Feb at 0.14/366.
        // Opening balance = 1011.09 (slightly less than 1011.89 in non-leap year).
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2024, 2, 1),
                3
        );

        assertEquals(3, schedule.size());
        var mar = schedule.getFirst();
        assertEquals(YearMonth.of(2024, 3), mar.month());
        assertEquals(new BigDecimal("1011.09"), mar.openingBalance());

        // March 2024 has 31 days, still leap year: rate = 0.14/366
        assertEquals(new BigDecimal("23.08"), mar.interestCharged());
    }

    @Test
    void leapYear_endOfFebruary_minimalPreBillingInterest() {
        // 1000 EUR on Feb 28 2024 (leap year). Pre-billing: Feb 28-29 = 2 days.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2024, 2, 28),
                3
        );

        var mar = schedule.getFirst();
        assertEquals(new BigDecimal("1000.77"), mar.openingBalance());
        // Pre-billing interest = 0.77 EUR (2 days at 0.14/366)
    }

    // --- Payment allocation ---

    @Test
    void paymentAllocation_interestPaidFirst_thenPrincipal() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                3
        );

        var feb = schedule.get(0);
        var mar = schedule.get(1);

        // Payment must exceed displayed interest to reduce principal
        assertTrue(feb.paymentAmount().compareTo(feb.interestCharged()) > 0,
                "Payment must exceed interest charged");

        // Month 2 opening = month 1 closing
        assertEquals(feb.closingBalance(), mar.openingBalance(),
                "Month 2 opening must equal month 1 closing");

        // Final balance = 0
        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO),
                "Final closing balance must be zero");
    }

    // --- Interest capitalization ---

    @Test
    void interestCapitalization_unpaidInterestAddsToPrincipal() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                6
        );

        var feb = schedule.get(0);
        var mar = schedule.get(1);

        // Feb closing = opening + interest_for_feb_only - payment
        // Note: displayed interest includes pre-billing, but only Feb interest is
        // added to balance within Feb (pre-billing already in opening balance).
        // So: closing = opening + (displayed_interest - pre_billing) - payment
        // = opening + feb_interest_only - payment
        // But since opening already includes pre-billing interest,
        // closing = opening + feb_month_interest - payment
        // feb_month_interest = displayed_interest - pre_billing_interest
        // pre_billing = opening - 1000.00 = 1011.89 - 1000.00 = 11.89
        var preBilling = feb.openingBalance().subtract(new BigDecimal("1000.00"));
        var febMonthInterest = feb.interestCharged().subtract(preBilling);
        var expectedFebClosing = feb.openingBalance()
                .add(febMonthInterest)
                .subtract(feb.paymentAmount());
        assertEquals(0, expectedFebClosing.compareTo(feb.closingBalance()),
                "Feb closing = opening + feb_month_interest - payment");

        assertEquals(feb.closingBalance(), mar.openingBalance());
    }

    // --- Minimum payment ---

    @Test
    void minimumPayment_fivePercentOrFiveEur_whicheverHigher() {
        assertEquals(new BigDecimal("50.00"), calculator.calculateMinimumPayment(new BigDecimal("1000.00")));
        assertEquals(new BigDecimal("5.00"), calculator.calculateMinimumPayment(new BigDecimal("80.00")));
        assertEquals(new BigDecimal("5.00"), calculator.calculateMinimumPayment(new BigDecimal("50.00")));
        assertEquals(new BigDecimal("5.25"), calculator.calculateMinimumPayment(new BigDecimal("105.00")));
        // Balance below floor: minimum capped at balance
        assertEquals(new BigDecimal("3.00"), calculator.calculateMinimumPayment(new BigDecimal("3.00")));
        // Zero balance: minimum is zero
        assertEquals(new BigDecimal("0.00"), calculator.calculateMinimumPayment(BigDecimal.ZERO));
    }

    @Test
    void minimumPayment_scheduleNeverBelowMinimum() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                6
        );

        for (var statement : schedule) {
            assertTrue(statement.paymentAmount().compareTo(statement.minimumPayment()) >= 0,
                    "Payment " + statement.paymentAmount() +
                            " must be >= minimum " + statement.minimumPayment() +
                            " in " + statement.month());
        }
    }

    @Test
    void minimumPayment_adjustedWhenEqualPaymentBelowMinimum() {
        // 1700 EUR / 24 months. Equal payment below minimum for early months.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1700.00"),
                LocalDate.of(2025, 1, 1),
                24
        );

        assertEquals(24, schedule.size());
        assertEquals(new BigDecimal("1720.21"), schedule.getFirst().openingBalance());

        for (var statement : schedule) {
            assertTrue(statement.paymentAmount().compareTo(statement.minimumPayment()) >= 0,
                    "Payment " + statement.paymentAmount() +
                            " must be >= minimum " + statement.minimumPayment() +
                            " in " + statement.month());
        }

        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO),
                "Final closing balance must be zero");

        // Consecutive months linked
        for (var i = 1; i < schedule.size(); i++) {
            assertEquals(schedule.get(i - 1).closingBalance(), schedule.get(i).openingBalance(),
                    "Month " + (i + 1) + " opening must equal month " + i + " closing");
        }
    }

    @Test
    void minimumPayment_earlyMonthsPayMoreThanEqualPayment() {
        // 1700 EUR / 24 months: first payment should equal minimum (5% of opening balance).
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1700.00"),
                LocalDate.of(2025, 1, 1),
                24
        );

        var firstStatement = schedule.getFirst();
        assertEquals(firstStatement.minimumPayment(), firstStatement.paymentAmount(),
                "First payment should equal minimum payment when equal payment is below minimum");
    }

    // --- Long repayment / early payoff ---

    @Test
    void tooManyMonths_balancePaidOffEarly_remainingMonthsZero() {
        // 100 EUR / 100 months: balance reaches zero well before month 100.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("100.00"),
                LocalDate.of(2025, 1, 1),
                100
        );

        assertEquals(100, schedule.size());

        for (var statement : schedule) {
            assertTrue(statement.paymentAmount().compareTo(statement.minimumPayment()) >= 0,
                    "Payment " + statement.paymentAmount() +
                            " must be >= minimum " + statement.minimumPayment() +
                            " in " + statement.month());
        }

        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO));

        // Balance should reach zero before month 100
        var lastNonZeroMonth = schedule.stream()
                .filter(s -> s.openingBalance().compareTo(BigDecimal.ZERO) > 0)
                .reduce((first, second) -> second);
        assertTrue(lastNonZeroMonth.isPresent());
        assertEquals(0, lastNonZeroMonth.get().closingBalance().compareTo(BigDecimal.ZERO),
                "Balance should reach zero before all months are used");
    }

    // --- Equal payments ---

    @Test
    void equalPayments_uniformWhenAboveMinimum() {
        // 1500 EUR / 6 months. Equal payment high enough to meet minimum.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                LocalDate.of(2025, 1, 1),
                6
        );

        assertEquals(6, schedule.size());

        var firstPayment = schedule.getFirst().paymentAmount();
        for (var i = 0; i < 5; i++) {
            assertEquals(firstPayment, schedule.get(i).paymentAmount(),
                    "Month " + (i + 1) + " payment should equal first payment");
        }
    }

    // --- Final balance ---

    @Test
    void multiMonthSchedule_finalBalanceZero() {
        for (var months : new int[]{2, 3, 6, 12, 24}) {
            var schedule = calculator.calculatePaymentSchedule(
                    new BigDecimal("1500.00"),
                    LocalDate.of(2025, 1, 1),
                    months
            );

            assertEquals(months, schedule.size());
            assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO),
                    "Final balance must be zero for " + months + "-month schedule");
        }
    }

    @Test
    void multiMonthSchedule_consecutiveMonthsLinked() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                LocalDate.of(2025, 1, 1),
                6
        );

        for (var i = 1; i < schedule.size(); i++) {
            assertEquals(schedule.get(i - 1).closingBalance(), schedule.get(i).openingBalance(),
                    "Month " + (i + 1) + " opening must equal month " + i + " closing");
        }
    }

    // --- Total interest ---

    @Test
    void totalInterest_multiMonth_greaterThanZero() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1500.00"),
                LocalDate.of(2025, 1, 1),
                6
        );

        var totalInterest = schedule.stream()
                .map(MonthlyStatement::interestCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalInterest.compareTo(BigDecimal.ZERO) > 0, "Total interest must be positive");

        var totalPaid = schedule.stream()
                .map(MonthlyStatement::paymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total paid = spend + total interest
        var expectedTotalPaid = new BigDecimal("1500.00").add(totalInterest);
        assertEquals(0, expectedTotalPaid.compareTo(totalPaid),
                "Total paid must equal spend + total interest");
    }

    // --- Grace period ---

    @Test
    void gracePeriod_payFullInOneMonth_zeroInterest() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                1
        );

        assertEquals(1, schedule.size());

        var feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1000.00"), feb.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.interestCharged());
        assertEquals(new BigDecimal("1000.00"), feb.paymentAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.closingBalance());
    }

    @Test
    void gracePeriod_payInTwoMonths_zeroInterest() {
        // Spend 1600 EUR on Oct 15, repay over 2 months.
        // Month 1 (Oct): voluntary early payment of half (800 EUR), 0 interest.
        // Month 2 (Nov): remaining half (800 EUR), 0 interest.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1600.00"),
                LocalDate.of(2025, 10, 15),
                2
        );

        assertEquals(2, schedule.size());

        var oct = schedule.get(0);
        assertEquals(YearMonth.of(2025, 10), oct.month());
        assertEquals(new BigDecimal("1600.00"), oct.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), oct.interestCharged());
        assertEquals(new BigDecimal("800.00"), oct.paymentAmount());
        assertEquals(new BigDecimal("800.00"), oct.closingBalance());
        assertEquals(new BigDecimal("80.00"), oct.minimumPayment());

        var nov = schedule.get(1);
        assertEquals(YearMonth.of(2025, 11), nov.month());
        assertEquals(new BigDecimal("800.00"), nov.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), nov.interestCharged());
        assertEquals(new BigDecimal("800.00"), nov.paymentAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), nov.closingBalance());
        assertEquals(new BigDecimal("40.00"), nov.minimumPayment());
    }

    @Test
    void gracePeriod_twoMonths_enforcesMinimumPaymentFloor() {
        // Spend 8 EUR on Jan 1, repay over 2 months.
        // Half would be 4.00, but minimum is 5.00 EUR.
        // Month 1 payment = 5.00, closing = 3.00.
        // Month 2 payment = 3.00, closing = 0.00.
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("8.00"),
                LocalDate.of(2025, 1, 1),
                2
        );

        assertEquals(2, schedule.size());

        var jan = schedule.get(0);
        assertEquals(YearMonth.of(2025, 1), jan.month());
        assertEquals(new BigDecimal("8.00"), jan.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), jan.interestCharged());
        assertEquals(new BigDecimal("5.00"), jan.paymentAmount());
        assertEquals(new BigDecimal("3.00"), jan.closingBalance());

        var feb = schedule.get(1);
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("3.00"), feb.openingBalance());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.interestCharged());
        assertEquals(new BigDecimal("3.00"), feb.paymentAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), feb.closingBalance());
    }

    // --- Mid-month purchase ---

    @Test
    void midMonthPurchase_finalBalanceZero() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 15),
                3
        );

        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void midMonthPurchase_consecutiveMonthsLinked() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 15),
                6
        );

        for (var i = 1; i < schedule.size(); i++) {
            assertEquals(schedule.get(i - 1).closingBalance(), schedule.get(i).openingBalance(),
                    "Month " + (i + 1) + " opening must equal month " + i + " closing");
        }
    }

    // --- Leap year ---

    @Test
    void leapYear_finalBalanceZero() {
        var schedule = calculator.calculatePaymentSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2024, 2, 1),
                6
        );

        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO));
    }
}
