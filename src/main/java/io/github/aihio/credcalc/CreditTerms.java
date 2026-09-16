package io.github.aihio.credcalc;

import java.math.BigDecimal;

/**
 * Encapsulates the pricing and policy parameters for a credit card product.
 *
 * @param annualRate            nominal annual interest rate (e.g. 0.14 for 14%)
 * @param minimumPaymentPercent minimum payment percentage of opening balance (e.g. 0.05 for 5%)
 * @param minimumPaymentFloor   minimum payment absolute floor amount in EUR (e.g. 5.00)
 */
public record CreditTerms(
        BigDecimal annualRate,
        BigDecimal minimumPaymentPercent,
        BigDecimal minimumPaymentFloor
) {
    public static CreditTerms revolut() {
        return new CreditTerms(
                new BigDecimal("0.14"),
                new BigDecimal("0.05"),
                new BigDecimal("5.00")
        );
    }
}
