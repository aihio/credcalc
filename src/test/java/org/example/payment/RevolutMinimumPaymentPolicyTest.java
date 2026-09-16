package org.example.payment;

import org.example.CreditTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevolutMinimumPaymentPolicyTest {

    private final MinimumPaymentPolicy policy = new RevolutMinimumPaymentPolicy(CreditTerms.revolut());

    @Test
    void calculate_aboveFloor_returnsFivePercent() {
        // 5% of 1000 = 50.00
        assertEquals(new BigDecimal("50.00"), policy.calculateMinimumPayment(new BigDecimal("1000.00")));
        // 5% of 105 = 5.25
        assertEquals(new BigDecimal("5.25"), policy.calculateMinimumPayment(new BigDecimal("105.00")));
    }

    @Test
    void calculate_belowFloor_returnsFloor() {
        // 5% of 80 = 4.00, floor = 5.00
        assertEquals(new BigDecimal("5.00"), policy.calculateMinimumPayment(new BigDecimal("80.00")));
        assertEquals(new BigDecimal("5.00"), policy.calculateMinimumPayment(new BigDecimal("50.00")));
    }

    @Test
    void calculate_belowFloorBalance_cappedAtBalance() {
        // Balance is 3.00, minimum capped at 3.00
        assertEquals(new BigDecimal("3.00"), policy.calculateMinimumPayment(new BigDecimal("3.00")));
    }

    @Test
    void calculate_zeroOrNegativeBalance_returnsZero() {
        assertEquals(new BigDecimal("0.00"), policy.calculateMinimumPayment(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), policy.calculateMinimumPayment(new BigDecimal("-10.00")));
    }
}
