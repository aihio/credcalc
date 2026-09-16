package org.example.payment;

import java.math.BigDecimal;

/**
 * Calculates the required minimum payment for a billing cycle.
 */
public interface MinimumPaymentPolicy {

    /**
     * Calculates minimum payment for given balance.
     * Capped at balance itself, and zero if balance is zero or negative.
     */
    BigDecimal calculateMinimumPayment(BigDecimal balance);
}
