package io.github.aihio.credcalc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Encapsulates the pricing and policy parameters for a credit card product.
 *
 * @param annualRate            nominal annual interest rate (e.g. 0.14 for 14%)
 * @param minimumPaymentPercent minimum payment percentage of opening balance (e.g. 0.05 for 5%)
 * @param minimumPaymentFloor   minimum payment absolute floor amount in EUR (e.g. 5.00)
 */
public record CreditTerms(
        String name,
        String currency,
        BigDecimal annualRate,
        BigDecimal minimumPaymentPercent,
        BigDecimal minimumPaymentFloor,
        int statementDay,
        int paymentDueDay
) {
    public CreditTerms {
        name = requireText(name, "name");
        currency = requireText(currency, "currency");
        annualRate = requireNonNegative(annualRate, "annualRate");
        minimumPaymentPercent = requireNonNegative(minimumPaymentPercent, "minimumPaymentPercent");
        minimumPaymentFloor = requireNonNegative(minimumPaymentFloor, "minimumPaymentFloor");
        validateDay(statementDay, "statementDay");
        validateDay(paymentDueDay, "paymentDueDay");
    }

    public CreditTerms(
            BigDecimal annualRate, BigDecimal minimumPaymentPercent, BigDecimal minimumPaymentFloor) {
        this("Revolut Credit Card", "EUR", annualRate, minimumPaymentPercent, minimumPaymentFloor, 31, 15);
    }

    public static CreditTerms revolut() {
        return new CreditTerms(
                "Revolut Credit Card",
                "EUR",
                new BigDecimal("0.14"),
                new BigDecimal("0.05"),
                new BigDecimal("5.00"),
                31,
                15
        );
    }

    public LocalDate statementDateFor(LocalDate purchaseDate) {
        var statementDate = purchaseDate.withDayOfMonth(Math.min(statementDay, purchaseDate.lengthOfMonth()));
        if (purchaseDate.isAfter(statementDate)) {
            var nextMonth = YearMonth.from(purchaseDate).plusMonths(1);
            statementDate = nextMonth.atDay(Math.min(statementDay, nextMonth.lengthOfMonth()));
        }
        return statementDate;
    }

    public LocalDate dueDateFor(LocalDate purchaseDate) {
        var dueMonth = YearMonth.from(statementDateFor(purchaseDate)).plusMonths(1);
        return dueMonth.atDay(Math.min(paymentDueDay, dueMonth.lengthOfMonth()));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static void validateDay(int value, String name) {
        if (value < 1 || value > 31) {
            throw new IllegalArgumentException(name + " must be from 1 to 31");
        }
    }
}
