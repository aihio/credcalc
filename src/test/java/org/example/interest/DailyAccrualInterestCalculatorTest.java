package org.example.interest;

import org.example.CreditTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyAccrualInterestCalculatorTest {

    private final DailyAccrualInterestCalculator calculator = new DailyAccrualInterestCalculator(CreditTerms.revolut());

    @Test
    void dailyRate_regularYear_dividesBy365() {
        var rate = calculator.dailyRate(2025);
        // 0.14 / 365 = 0.0003835616...
        var expected = new BigDecimal("0.14").divide(new BigDecimal("365"), 10, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, rate);
    }

    @Test
    void dailyRate_leapYear_dividesBy366() {
        var rate = calculator.dailyRate(2024);
        var expected = new BigDecimal("0.14").divide(new BigDecimal("366"), 10, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, rate);
    }

    @Test
    void preBillingInterest_jan1ToJan31_31Days() {
        // 1000 EUR on Jan 1 2025: 31 days
        var interest = calculator.calculatePreBillingInterest(new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));
        assertEquals(new BigDecimal("11.89"), interest);
    }

    @Test
    void monthInterest_feb2025_28Days() {
        // 1000 EUR in Feb 2025: 28 days
        var interest = calculator.calculateMonthInterest(new BigDecimal("1000.00"), YearMonth.of(2025, 2));
        assertEquals(new BigDecimal("10.74"), interest);
    }
}
