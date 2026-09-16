package io.github.aihio.credcalc.schedule;

import io.github.aihio.credcalc.MonthlyStatement;
import io.github.aihio.credcalc.interest.InterestCalculator;
import io.github.aihio.credcalc.payment.MinimumPaymentPolicy;
import io.github.aihio.credcalc.payment.PaymentSolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class InstallmentScheduleBuilder implements ScheduleBuilder {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private final InterestCalculator interestCalculator;
    private final MinimumPaymentPolicy minimumPaymentPolicy;
    private final PaymentSolver paymentSolver;

    public InstallmentScheduleBuilder(
            InterestCalculator interestCalculator,
            MinimumPaymentPolicy minimumPaymentPolicy,
            PaymentSolver paymentSolver) {
        this.interestCalculator = interestCalculator;
        this.minimumPaymentPolicy = minimumPaymentPolicy;
        this.paymentSolver = paymentSolver;
    }

    @Override
    public boolean supports(int numberOfMonths) {
        return numberOfMonths > 2;
    }

    @Override
    public List<MonthlyStatement> buildSchedule(
            BigDecimal spendAmount, LocalDate purchaseDate, int numberOfMonths) {

        if (!supports(numberOfMonths)) {
            throw new IllegalArgumentException("Unsupported repayment duration: " + numberOfMonths);
        }

        var preBillingInterest = interestCalculator.calculatePreBillingInterest(spendAmount, purchaseDate);
        var adjustedBalance = spendAmount.add(preBillingInterest).setScale(SCALE, ROUNDING);
        var firstBillingMonth = YearMonth.from(purchaseDate).plusMonths(1);

        var monthlyPayment = paymentSolver.findEqualPayment(adjustedBalance, firstBillingMonth, numberOfMonths);

        var statements = new ArrayList<MonthlyStatement>();
        var balance = adjustedBalance;

        for (var i = 0; i < numberOfMonths; i++) {
            var currentMonth = firstBillingMonth.plusMonths(i);

            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                statements.add(new MonthlyStatement(
                        currentMonth, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED));
                continue;
            }

            var currentOpening = balance.setScale(SCALE, ROUNDING);
            var monthInterest = interestCalculator.calculateMonthInterest(balance, currentMonth);
            balance = balance.add(monthInterest);

            var minimumPayment = minimumPaymentPolicy.calculateMinimumPayment(currentOpening);
            var effectivePayment = monthlyPayment.max(minimumPayment);

            var isTerminal = (i == numberOfMonths - 1 || balance.compareTo(effectivePayment) <= 0);
            var actualPayment = isTerminal
                    ? balance.setScale(SCALE, ROUNDING)
                    : effectivePayment.setScale(SCALE, ROUNDING);

            balance = balance.subtract(actualPayment);
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                balance = BigDecimal.ZERO;
            }

            var displayedInterest = (i == 0)
                    ? preBillingInterest.add(monthInterest).setScale(SCALE, ROUNDING)
                    : monthInterest;

            statements.add(new MonthlyStatement(
                    currentMonth, currentOpening, displayedInterest,
                    actualPayment.setScale(SCALE, ROUNDING),
                    balance.setScale(SCALE, ROUNDING), minimumPayment));
        }

        return statements;
    }
}
