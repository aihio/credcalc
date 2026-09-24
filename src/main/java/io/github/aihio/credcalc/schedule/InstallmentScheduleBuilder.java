package io.github.aihio.credcalc.schedule;

import io.github.aihio.credcalc.MonthlyStatement;
import io.github.aihio.credcalc.CreditTerms;
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
    private final CreditTerms terms;

    public InstallmentScheduleBuilder(
            InterestCalculator interestCalculator,
            MinimumPaymentPolicy minimumPaymentPolicy,
            PaymentSolver paymentSolver) {
        this(interestCalculator, minimumPaymentPolicy, paymentSolver, CreditTerms.revolut());
    }

    public InstallmentScheduleBuilder(
            InterestCalculator interestCalculator,
            MinimumPaymentPolicy minimumPaymentPolicy,
            PaymentSolver paymentSolver,
            CreditTerms terms) {
        this.interestCalculator = interestCalculator;
        this.minimumPaymentPolicy = minimumPaymentPolicy;
        this.paymentSolver = paymentSolver;
        this.terms = terms;
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
        var firstDueDate = terms.dueDateFor(purchaseDate);
        var firstPeriodStart = terms.statementDateFor(purchaseDate).plusDays(1);

        var monthlyPayment = paymentSolver.findEqualPayment(
                adjustedBalance, firstPeriodStart, firstDueDate, terms.paymentDueDay(), numberOfMonths);

        var statements = new ArrayList<MonthlyStatement>();
        var balance = adjustedBalance;
        var periodStart = firstPeriodStart;

        for (var i = 0; i < numberOfMonths; i++) {
            var dueMonth = YearMonth.from(firstDueDate).plusMonths(i);
            var dueDate = dueMonth.atDay(Math.min(terms.paymentDueDay(), dueMonth.lengthOfMonth()));
            var currentMonth = YearMonth.from(dueDate);

            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                statements.add(new MonthlyStatement(
                        currentMonth, dueDate, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED, ZERO_SCALED));
                continue;
            }

            var currentOpening = balance.setScale(SCALE, ROUNDING);
            var periodInterest = interestCalculator.calculateInterest(balance, periodStart, dueDate);
            balance = balance.add(periodInterest);

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
                    ? preBillingInterest.add(periodInterest).setScale(SCALE, ROUNDING)
                    : periodInterest;

            statements.add(new MonthlyStatement(
                    currentMonth, dueDate, currentOpening, displayedInterest,
                    actualPayment.setScale(SCALE, ROUNDING),
                    balance.setScale(SCALE, ROUNDING), minimumPayment));
            periodStart = dueDate.plusDays(1);
        }

        return statements;
    }
}
