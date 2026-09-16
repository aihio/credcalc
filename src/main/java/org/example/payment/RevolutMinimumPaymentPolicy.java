package org.example.payment;

import org.example.CreditTerms;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RevolutMinimumPaymentPolicy implements MinimumPaymentPolicy {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private final CreditTerms terms;

    public RevolutMinimumPaymentPolicy(CreditTerms terms) {
        this.terms = terms;
    }

    @Override
    public BigDecimal calculateMinimumPayment(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_SCALED;
        }
        var fivePercent = balance.multiply(terms.minimumPaymentPercent()).setScale(SCALE, ROUNDING);
        var minimum = fivePercent.max(terms.minimumPaymentFloor());
        return minimum.min(balance.setScale(SCALE, ROUNDING));
    }
}
