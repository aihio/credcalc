package io.github.aihio.credcalc.payment;

import io.github.aihio.credcalc.CreditTerms;
import io.github.aihio.credcalc.interest.DailyAccrualInterestCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BinarySearchPaymentSolverTest {

    private final CreditTerms terms = CreditTerms.revolut();
    private final PaymentSolver solver = new BinarySearchPaymentSolver(
            new DailyAccrualInterestCalculator(terms),
            new RevolutMinimumPaymentPolicy(terms)
    );

    @Test
    void findEqualPayment_threeMonths_returnsReasonableMonthlyPayment() {
        // 1011.89 over 3 months starting Feb 2025
        var payment = solver.findEqualPayment(
                new BigDecimal("1011.89"),
                YearMonth.of(2025, 2),
                3
        );

        // Payment should be roughly 1011.89 / 3 ~ 337 + interest (~345-355)
        assertTrue(payment.compareTo(new BigDecimal("340.00")) > 0);
        assertTrue(payment.compareTo(new BigDecimal("360.00")) < 0);
    }

    @Test
    void findEqualPayment_longPeriod_paymentNeverBelowMinimum() {
        // 1720.21 over 24 months
        var payment = solver.findEqualPayment(
                new BigDecimal("1720.21"),
                YearMonth.of(2025, 2),
                24
        );

        // Payment must be positive and bounded
        assertTrue(payment.compareTo(new BigDecimal("50.00")) > 0);
        assertTrue(payment.compareTo(new BigDecimal("120.00")) < 0);
    }
}
