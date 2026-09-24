package io.github.aihio.credcalc.payment;

import io.github.aihio.credcalc.interest.InterestCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.LocalDate;

public class BinarySearchPaymentSolver implements PaymentSolver {

    private static final int BINARY_SEARCH_MAX_ITERATIONS = 100;
    private static final BigDecimal BINARY_SEARCH_TOLERANCE = new BigDecimal("0.01");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InterestCalculator interestCalculator;
    private final MinimumPaymentPolicy minimumPaymentPolicy;

    public BinarySearchPaymentSolver(
            InterestCalculator interestCalculator,
            MinimumPaymentPolicy minimumPaymentPolicy) {
        this.interestCalculator = interestCalculator;
        this.minimumPaymentPolicy = minimumPaymentPolicy;
    }

    @Override
    public BigDecimal findEqualPayment(BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths) {
        var low = new BigDecimal("0.01");
        var high = openingBalance.multiply(BigDecimal.TWO);

        for (var i = 0; i < BINARY_SEARCH_MAX_ITERATIONS; i++) {
            var mid = low.add(high).divide(BigDecimal.TWO, 10, ROUNDING);
            var finalBalance = simulateBalance(openingBalance, firstBillingMonth, numberOfMonths, mid);

            if (finalBalance.abs().compareTo(BINARY_SEARCH_TOLERANCE) <= 0) {
                return mid.setScale(SCALE, ROUNDING);
            }
            if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low.add(high).divide(BigDecimal.TWO, SCALE, ROUNDING);
    }

    @Override
    public BigDecimal findEqualPayment(
            BigDecimal openingBalance,
            LocalDate firstPeriodStart,
            LocalDate firstDueDate,
            int paymentDueDay,
            int numberOfMonths) {
        var low = new BigDecimal("0.01");
        var high = openingBalance.multiply(BigDecimal.TWO);

        for (var i = 0; i < BINARY_SEARCH_MAX_ITERATIONS; i++) {
            var mid = low.add(high).divide(BigDecimal.TWO, 10, ROUNDING);
            var finalBalance = simulateBalance(
                    openingBalance, firstPeriodStart, firstDueDate, paymentDueDay, numberOfMonths, mid);

            if (finalBalance.abs().compareTo(BINARY_SEARCH_TOLERANCE) <= 0) {
                return mid.setScale(SCALE, ROUNDING);
            }
            if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low.add(high).divide(BigDecimal.TWO, SCALE, ROUNDING);
    }

    private BigDecimal simulateBalance(
            BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths, BigDecimal monthlyPayment) {

        var balance = openingBalance;
        for (var i = 0; i < numberOfMonths; i++) {
            var currentMonth = firstBillingMonth.plusMonths(i);

            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            var currentOpening = balance.setScale(SCALE, ROUNDING);
            var monthInterest = interestCalculator.calculateMonthInterest(balance, currentMonth);
            balance = balance.add(monthInterest);

            var minimumPayment = minimumPaymentPolicy.calculateMinimumPayment(currentOpening);
            var effectivePayment = monthlyPayment.max(minimumPayment);

            balance = balance.subtract(effectivePayment.setScale(SCALE, ROUNDING));
        }
        return balance.setScale(SCALE, ROUNDING);
    }

    private BigDecimal simulateBalance(
            BigDecimal openingBalance,
            LocalDate firstPeriodStart,
            LocalDate firstDueDate,
            int paymentDueDay,
            int numberOfMonths,
            BigDecimal monthlyPayment) {

        var balance = openingBalance;
        var periodStart = firstPeriodStart;
        for (var i = 0; i < numberOfMonths; i++) {
            var dueMonth = YearMonth.from(firstDueDate).plusMonths(i);
            var dueDate = dueMonth.atDay(Math.min(paymentDueDay, dueMonth.lengthOfMonth()));
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            var currentOpening = balance.setScale(SCALE, ROUNDING);
            var periodInterest = interestCalculator.calculateInterest(balance, periodStart, dueDate);
            balance = balance.add(periodInterest);

            var minimumPayment = minimumPaymentPolicy.calculateMinimumPayment(currentOpening);
            var effectivePayment = monthlyPayment.max(minimumPayment);
            balance = balance.subtract(effectivePayment.setScale(SCALE, ROUNDING));
            periodStart = dueDate.plusDays(1);
        }
        return balance.setScale(SCALE, ROUNDING);
    }
}
