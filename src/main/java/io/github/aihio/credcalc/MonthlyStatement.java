package io.github.aihio.credcalc;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Represents one month's billing statement in a credit card payment schedule.
 *
 * @param month            the billing month
 * @param openingBalance   balance at start of month (principal + any capitalized interest from prior months)
 * @param interestCharged  total daily-accrued interest for this month
 * @param paymentAmount    amount paid at end of month (applied: interest first, then principal)
 * @param closingBalance   balance after payment and interest capitalization
 * @param minimumPayment   minimum required payment: max(5% of opening balance, 5.00 EUR)
 */
public record MonthlyStatement(
        YearMonth month,
        BigDecimal openingBalance,
        BigDecimal interestCharged,
        BigDecimal paymentAmount,
        BigDecimal closingBalance,
        BigDecimal minimumPayment
) {
}
