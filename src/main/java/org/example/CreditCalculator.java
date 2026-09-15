package org.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a credit card payment schedule with Revolut-style rules:
 * <ul>
 *   <li>Annual rate: 14%, daily rate = 14% / 365</li>
 *   <li>Grace period: purchases in month M paid in full by end of M+1 incur no interest</li>
 *   <li>Daily simple interest accrual on outstanding balance</li>
 *   <li>Monthly interest capitalization (unpaid interest added to principal)</li>
 *   <li>Payment allocation: accrued interest first, then principal</li>
 *   <li>Minimum payment: max(5% of balance, 5.00 EUR)</li>
 * </ul>
 */
public class CreditCalculator {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.14");
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal DAILY_RATE = ANNUAL_RATE.divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP);
    private static final BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("5.00");
    private static final BigDecimal MINIMUM_PAYMENT_PERCENT = new BigDecimal("0.05");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Generate a payment schedule for a credit card spend.
     *
     * @param spendAmount    total amount spent (EUR)
     * @param spendMonth     month in which the spend occurred
     * @param numberOfMonths number of months over which to repay
     * @return list of monthly statements, one per payment month
     */
    public List<MonthlyStatement> calculatePaymentSchedule(
            BigDecimal spendAmount, YearMonth spendMonth, int numberOfMonths) {

        if (spendAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be positive");
        }
        if (numberOfMonths <= 0) {
            throw new IllegalArgumentException("Number of months must be positive");
        }

        // Grace period: if numberOfMonths == 1, full payment by end of spendMonth+1, no interest
        if (numberOfMonths == 1) {
            return buildGracePeriodSchedule(spendAmount, spendMonth);
        }

        // For multi-month repayment, grace period is lost.
        // Find equal monthly payment via binary search.
        BigDecimal monthlyPayment = findEqualPayment(spendAmount, spendMonth, numberOfMonths);

        // Validate: payment must meet minimum for first month (highest balance)
        BigDecimal minimumPayment = calculateMinimumPayment(spendAmount);
        if (monthlyPayment.compareTo(minimumPayment) < 0) {
            throw new IllegalArgumentException(
                    "Repayment period too long: equal payment " + monthlyPayment +
                            " EUR is below minimum payment " + minimumPayment + " EUR");
        }

        return simulate(spendAmount, spendMonth, numberOfMonths, monthlyPayment);
    }

    private List<MonthlyStatement> buildGracePeriodSchedule(BigDecimal spendAmount, YearMonth spendMonth) {
        YearMonth paymentMonth = spendMonth.plusMonths(1);
        BigDecimal minimumPayment = calculateMinimumPayment(spendAmount);
        return List.of(new MonthlyStatement(
                paymentMonth,
                spendAmount,
                BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                spendAmount,
                BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                minimumPayment
        ));
    }

    /**
     * Binary search for equal monthly payment that zeroes balance by month N.
     */
    BigDecimal findEqualPayment(BigDecimal spendAmount, YearMonth spendMonth, int numberOfMonths) {
        BigDecimal low = new BigDecimal("0.01");
        BigDecimal high = spendAmount.multiply(new BigDecimal("2"));
        BigDecimal tolerance = new BigDecimal("0.01");

        for (int i = 0; i < 100; i++) {
            BigDecimal mid = low.add(high).divide(new BigDecimal("2"), 10, ROUNDING);
            BigDecimal finalBalance = simulateFinalBalance(spendAmount, spendMonth, numberOfMonths, mid);

            if (finalBalance.abs().compareTo(tolerance) <= 0) {
                return mid.setScale(SCALE, ROUNDING);
            }
            if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low.add(high).divide(new BigDecimal("2"), SCALE, ROUNDING);
    }

    /**
     * Fast simulation that returns only the final balance (for binary search).
     * Uses equal payment every month, including the last.
     */
    private BigDecimal simulateFinalBalance(
            BigDecimal spendAmount, YearMonth spendMonth, int numberOfMonths, BigDecimal monthlyPayment) {

        BigDecimal balance = spendAmount;

        for (int i = 1; i <= numberOfMonths; i++) {
            YearMonth currentMonth = spendMonth.plusMonths(i);
            int daysInMonth = currentMonth.lengthOfMonth();
            BigDecimal monthInterest = BigDecimal.ZERO;

            for (int day = 1; day <= daysInMonth; day++) {
                BigDecimal dailyInterest = balance.multiply(DAILY_RATE).setScale(10, ROUNDING);
                monthInterest = monthInterest.add(dailyInterest);
            }
            monthInterest = monthInterest.setScale(SCALE, ROUNDING);

            balance = balance.add(monthInterest).subtract(monthlyPayment);
        }
        return balance.setScale(SCALE, ROUNDING);
    }

    /**
     * Simulate month-by-month schedule with given fixed payment.
     * Grace period is NOT applied here (caller handles that case).
     */
    List<MonthlyStatement> simulate(
            BigDecimal spendAmount, YearMonth spendMonth, int numberOfMonths, BigDecimal monthlyPayment) {

        List<MonthlyStatement> statements = new ArrayList<>();
        BigDecimal balance = spendAmount;

        for (int i = 1; i <= numberOfMonths; i++) {
            YearMonth currentMonth = spendMonth.plusMonths(i);
            BigDecimal openingBalance = balance.setScale(SCALE, ROUNDING);

            // Daily interest accrual for this month
            // Balance is constant within a month (payment applied at month end)
            int daysInMonth = currentMonth.lengthOfMonth();
            BigDecimal monthInterest = BigDecimal.ZERO;

            for (int day = 1; day <= daysInMonth; day++) {
                BigDecimal dailyInterest = balance.multiply(DAILY_RATE).setScale(10, ROUNDING);
                monthInterest = monthInterest.add(dailyInterest);
            }
            monthInterest = monthInterest.setScale(SCALE, ROUNDING);

            // Capitalize interest: add to balance
            balance = balance.add(monthInterest);

            // Determine actual payment
            BigDecimal minimumPayment = calculateMinimumPayment(openingBalance);
            BigDecimal actualPayment;
            if (i == numberOfMonths) {
                // Last month: pay remaining balance
                actualPayment = balance.setScale(SCALE, ROUNDING);
            } else {
                actualPayment = monthlyPayment.setScale(SCALE, ROUNDING);
            }

            // Apply payment
            balance = balance.subtract(actualPayment);

            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                balance = BigDecimal.ZERO;
            }

            statements.add(new MonthlyStatement(
                    currentMonth,
                    openingBalance,
                    monthInterest,
                    actualPayment.setScale(SCALE, ROUNDING),
                    balance.setScale(SCALE, ROUNDING),
                    minimumPayment
            ));
        }
        return statements;
    }

    /**
     * Minimum payment: max(5% of balance, 5.00 EUR).
     */
    BigDecimal calculateMinimumPayment(BigDecimal balance) {
        BigDecimal fivePercent = balance.multiply(MINIMUM_PAYMENT_PERCENT).setScale(SCALE, ROUNDING);
        return fivePercent.max(MINIMUM_PAYMENT_FLOOR);
    }
}
