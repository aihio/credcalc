package org.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a credit card payment schedule with Revolut credit card rules:
 * <ul>
 *   <li>Annual rate: 14%, daily rate = 14% / actual days in year (365 or 366)</li>
 *   <li>Interest accrues daily from purchase date (not next month)</li>
 *   <li>Pre-billing interest (purchase date to end of purchase month) capitalizes
 *       into opening balance of first billing month</li>
 *   <li>Grace period: pay full balance within 1-2 months of purchase, no interest charged</li>
 *   <li>Daily simple interest accrual on outstanding balance</li>
 *   <li>Monthly interest capitalization (unpaid interest added to principal)</li>
 *   <li>Payment allocation: accrued interest first, then principal</li>
 *   <li>Minimum payment: max(5% of balance, 5.00 EUR), capped at balance</li>
 *   <li>When equal payment falls below minimum, that month pays the minimum instead</li>
 * </ul>
 */
public class CreditCalculator {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.14");
    private static final BigDecimal MINIMUM_PAYMENT_FLOOR = new BigDecimal("5.00");
    private static final BigDecimal MINIMUM_PAYMENT_PERCENT = new BigDecimal("0.05");
    private static final int BINARY_SEARCH_MAX_ITERATIONS = 100;
    private static final BigDecimal BINARY_SEARCH_TOLERANCE = new BigDecimal("0.01");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    /**
     * Generate a payment schedule for a credit card spend.
     *
     * @param spendAmount    total amount spent (EUR)
     * @param purchaseDate   date on which the spend occurred
     * @param numberOfMonths number of billing months over which to repay
     * @return list of monthly statements, one per billing month
     */
    public List<MonthlyStatement> calculatePaymentSchedule(
            BigDecimal spendAmount, LocalDate purchaseDate, int numberOfMonths) {

        if (spendAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Spend amount must be positive");
        }
        if (numberOfMonths <= 0) {
            throw new IllegalArgumentException("Number of months must be positive");
        }

        // Grace period: up to 2 interest-free months
        if (numberOfMonths <= 2) {
            return buildGracePeriodSchedule(spendAmount, purchaseDate, numberOfMonths);
        }

        // For multi-month repayment, grace period is lost.
        // Interest accrues from purchase date.
        var preBillingInterest = calculatePreBillingInterest(spendAmount, purchaseDate);
        var adjustedBalance = spendAmount.add(preBillingInterest).setScale(SCALE, ROUNDING);
        var firstBillingMonth = YearMonth.from(purchaseDate).plusMonths(1);

        // Find equal monthly payment via binary search (minimum-payment aware).
        var monthlyPayment = findEqualPayment(adjustedBalance, firstBillingMonth, numberOfMonths);

        return buildSchedule(adjustedBalance, preBillingInterest, firstBillingMonth, numberOfMonths, monthlyPayment);
    }

    /**
     * Build an interest-free grace period schedule.
     *
     * <p>1 month: single full payment due by end of month after purchase.
     * <p>2 months: split principal across purchase month (voluntary early payment)
     * and month after purchase (statement due date). Minimum payment enforced.
     */
    private List<MonthlyStatement> buildGracePeriodSchedule(
            BigDecimal spendAmount, LocalDate purchaseDate, int numberOfMonths) {

        var purchaseMonth = YearMonth.from(purchaseDate);

        if (numberOfMonths == 1) {
            var paymentMonth = purchaseMonth.plusMonths(1);
            var minimumPayment = calculateMinimumPayment(spendAmount);
            return List.of(new MonthlyStatement(
                    paymentMonth, spendAmount, ZERO_SCALED, spendAmount, ZERO_SCALED, minimumPayment));
        }

        // 2-month grace: split principal, enforce minimum on first payment
        var minimumFirst = calculateMinimumPayment(spendAmount);
        var halfPayment = spendAmount.divide(BigDecimal.TWO, SCALE, ROUNDING);
        var firstPayment = halfPayment.max(minimumFirst);
        var remainingBalance = spendAmount.subtract(firstPayment).setScale(SCALE, ROUNDING);
        var minimumSecond = calculateMinimumPayment(remainingBalance);

        return List.of(
                new MonthlyStatement(purchaseMonth, spendAmount, ZERO_SCALED,
                        firstPayment, remainingBalance, minimumFirst),
                new MonthlyStatement(purchaseMonth.plusMonths(1), remainingBalance, ZERO_SCALED,
                        remainingBalance, ZERO_SCALED, minimumSecond));
    }

    // --- Interest calculation helpers ---

    /**
     * Daily rate for a given year: annual rate / actual days in year.
     */
    private static BigDecimal dailyRate(int year) {
        return ANNUAL_RATE.divide(BigDecimal.valueOf(Year.of(year).length()), 10, ROUNDING);
    }

    /**
     * Accrue daily simple interest over a number of days.
     *
     * @param balance  principal on which interest accrues
     * @param days     number of days to accrue
     * @param year     calendar year (determines daily rate via actual year length)
     * @return total interest, rounded to SCALE
     */
    private static BigDecimal accrueDailyInterest(BigDecimal balance, int days, int year) {
        var rate = dailyRate(year);
        var interest = BigDecimal.ZERO;
        for (var day = 0; day < days; day++) {
            interest = interest.add(balance.multiply(rate).setScale(10, ROUNDING));
        }
        return interest.setScale(SCALE, ROUNDING);
    }

    /**
     * Interest from purchase date to end of purchase month (pre-billing period).
     */
    private BigDecimal calculatePreBillingInterest(BigDecimal balance, LocalDate purchaseDate) {
        var endOfMonth = purchaseDate.withDayOfMonth(purchaseDate.lengthOfMonth());
        var days = (int) ChronoUnit.DAYS.between(purchaseDate, endOfMonth) + 1;
        return accrueDailyInterest(balance, days, purchaseDate.getYear());
    }

    /**
     * Interest accrued over a full billing month.
     */
    private BigDecimal calculateMonthInterest(BigDecimal balance, YearMonth month) {
        return accrueDailyInterest(balance, month.lengthOfMonth(), month.getYear());
    }

    // --- Binary search ---

    BigDecimal findEqualPayment(BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths) {
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

    // --- Unified simulation core ---

    /**
     * Core simulation loop shared by binary search and schedule building.
     * Processes each billing month: interest accrual, minimum-payment enforcement, payment.
     *
     * @param openingBalance   balance at start of first billing month
     * @param firstBillingMonth first billing month
     * @param numberOfMonths   total billing months
     * @param monthlyPayment   candidate equal payment
     * @param statements       if non-null, MonthlyStatement records are appended here;
     *                         if null, only final balance is tracked (fast path for binary search)
     * @param preBillingInterest pre-billing interest to display in first month (ignored when statements is null)
     * @return final balance after all months
     */
    private BigDecimal simulateCore(
            BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths,
            BigDecimal monthlyPayment, List<MonthlyStatement> statements, BigDecimal preBillingInterest) {

        var balance = openingBalance;

        for (var i = 0; i < numberOfMonths; i++) {
            var currentMonth = firstBillingMonth.plusMonths(i);

            // Balance paid off: emit zero-balance rows in schedule mode, break in search mode
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                if (statements != null) {
                    statements.add(new MonthlyStatement(
                            currentMonth, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED));
                    continue;
                } else {
                    break;
                }
            }

            var currentOpening = balance.setScale(SCALE, ROUNDING);
            var monthInterest = calculateMonthInterest(balance, currentMonth);
            balance = balance.add(monthInterest);

            var minimumPayment = calculateMinimumPayment(currentOpening);
            var effectivePayment = monthlyPayment.max(minimumPayment);

            BigDecimal actualPayment;
            if (statements != null
                    && (i == numberOfMonths - 1 || balance.compareTo(effectivePayment) <= 0)) {
                // Schedule mode: clear remaining balance on last month or when balance is small
                actualPayment = balance.setScale(SCALE, ROUNDING);
            } else {
                // Binary search mode: always apply effective payment (no early clear)
                // Schedule mode non-terminal: apply effective payment
                actualPayment = effectivePayment.setScale(SCALE, ROUNDING);
            }

            balance = balance.subtract(actualPayment);
            if (statements != null && balance.compareTo(BigDecimal.ZERO) < 0) {
                balance = BigDecimal.ZERO;
            }

            if (statements != null) {
                var displayedInterest = (i == 0)
                        ? preBillingInterest.add(monthInterest).setScale(SCALE, ROUNDING)
                        : monthInterest;
                statements.add(new MonthlyStatement(
                        currentMonth, currentOpening, displayedInterest,
                        actualPayment.setScale(SCALE, ROUNDING),
                        balance.setScale(SCALE, ROUNDING), minimumPayment));
            }
        }
        return balance.setScale(SCALE, ROUNDING);
    }

    /**
     * Fast simulation returning only final balance (for binary search).
     */
    private BigDecimal simulateBalance(
            BigDecimal openingBalance, YearMonth firstBillingMonth, int numberOfMonths, BigDecimal monthlyPayment) {
        return simulateCore(openingBalance, firstBillingMonth, numberOfMonths, monthlyPayment, null, null);
    }

    /**
     * Full simulation producing monthly statements.
     */
    List<MonthlyStatement> buildSchedule(
            BigDecimal openingBalance, BigDecimal preBillingInterest,
            YearMonth firstBillingMonth, int numberOfMonths, BigDecimal monthlyPayment) {

        var statements = new ArrayList<MonthlyStatement>();
        simulateCore(openingBalance, firstBillingMonth, numberOfMonths, monthlyPayment, statements, preBillingInterest);
        return statements;
    }

    /**
     * Minimum payment: max(5% of balance, 5.00 EUR), capped at balance.
     * Returns zero when balance is zero or negative.
     */
    BigDecimal calculateMinimumPayment(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_SCALED;
        }
        var fivePercent = balance.multiply(MINIMUM_PAYMENT_PERCENT).setScale(SCALE, ROUNDING);
        var minimum = fivePercent.max(MINIMUM_PAYMENT_FLOOR);
        return minimum.min(balance.setScale(SCALE, ROUNDING));
    }
}
