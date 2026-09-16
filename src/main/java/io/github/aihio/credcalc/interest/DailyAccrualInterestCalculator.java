package io.github.aihio.credcalc.interest;

import io.github.aihio.credcalc.CreditTerms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class DailyAccrualInterestCalculator implements InterestCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final CreditTerms terms;

    public DailyAccrualInterestCalculator(CreditTerms terms) {
        this.terms = terms;
    }

    BigDecimal dailyRate(int year) {
        return terms.annualRate().divide(BigDecimal.valueOf(Year.of(year).length()), 10, ROUNDING);
    }

    BigDecimal accrueDailyInterest(BigDecimal balance, int days, int year) {
        var rate = dailyRate(year);
        var interest = BigDecimal.ZERO;
        for (var day = 0; day < days; day++) {
            interest = interest.add(balance.multiply(rate).setScale(10, ROUNDING));
        }
        return interest.setScale(SCALE, ROUNDING);
    }

    @Override
    public BigDecimal calculatePreBillingInterest(BigDecimal balance, LocalDate purchaseDate) {
        var endOfMonth = purchaseDate.withDayOfMonth(purchaseDate.lengthOfMonth());
        var days = (int) ChronoUnit.DAYS.between(purchaseDate, endOfMonth) + 1;
        return accrueDailyInterest(balance, days, purchaseDate.getYear());
    }

    @Override
    public BigDecimal calculateMonthInterest(BigDecimal balance, YearMonth month) {
        return accrueDailyInterest(balance, month.lengthOfMonth(), month.getYear());
    }
}
