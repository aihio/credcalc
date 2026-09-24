package io.github.aihio.credcalc.payment;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Solves for the fixed monthly payment amount that amortizes a balance over N months.
 */
public interface PaymentSolver {

    /**
     * Finds equal monthly payment needed to zero the balance after numberOfMonths.
     */
    BigDecimal findEqualPayment(BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths);

    /**
     * Finds equal payment for dated payment periods.
     */
    BigDecimal findEqualPayment(
            BigDecimal openingBalance,
            java.time.LocalDate firstPeriodStart,
            java.time.LocalDate firstDueDate,
            int paymentDueDay,
            int numberOfMonths);
}
