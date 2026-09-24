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
        return calculateInterest(balance, purchaseDate, terms.statementDateFor(purchaseDate));
    }

    @Override
    public BigDecimal calculateInterest(BigDecimal balance, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return accrueInterest(balance, startDate, endDate);
    }

    @Override
    public BigDecimal calculateMonthInterest(BigDecimal balance, YearMonth month) {
        return calculateInterest(balance, month.atDay(1), month.atEndOfMonth());
    }

    private BigDecimal accrueInterest(BigDecimal balance, LocalDate startDate, LocalDate endDate) {
        var total = BigDecimal.ZERO;
        var currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            var endOfYear = LocalDate.of(currentDate.getYear(), 12, 31);
            var segmentEnd = endDate.isBefore(endOfYear) ? endDate : endOfYear;
            var days = (int) ChronoUnit.DAYS.between(currentDate, segmentEnd) + 1;
            total = total.add(accrueDailyInterest(balance, days, currentDate.getYear()));
            currentDate = segmentEnd.plusDays(1);
        }
        return total.setScale(SCALE, ROUNDING);
    }
}
