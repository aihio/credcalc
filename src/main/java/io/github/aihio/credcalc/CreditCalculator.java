package io.github.aihio.credcalc;

import io.github.aihio.credcalc.interest.DailyAccrualInterestCalculator;
import io.github.aihio.credcalc.payment.BinarySearchPaymentSolver;
import io.github.aihio.credcalc.payment.MinimumPaymentPolicy;
import io.github.aihio.credcalc.payment.RevolutMinimumPaymentPolicy;
import io.github.aihio.credcalc.schedule.GracePeriodScheduleBuilder;
import io.github.aihio.credcalc.schedule.InstallmentScheduleBuilder;
import io.github.aihio.credcalc.schedule.ScheduleBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Facade for credit card payment schedule calculations.
 * Delegates schedule generation to matching {@link ScheduleBuilder} implementations
 * and minimum payment evaluation to {@link MinimumPaymentPolicy}.
 */
public class CreditCalculator {

    private final List<ScheduleBuilder> scheduleBuilders;
    private final MinimumPaymentPolicy minimumPaymentPolicy;

    public CreditCalculator() {
        this(CreditTerms.revolut());
    }

    public CreditCalculator(CreditTerms terms) {
        this(createDefaultBuilders(terms), new RevolutMinimumPaymentPolicy(terms));
    }

    public CreditCalculator(List<ScheduleBuilder> scheduleBuilders, MinimumPaymentPolicy minimumPaymentPolicy) {
        this.scheduleBuilders = List.copyOf(Objects.requireNonNull(scheduleBuilders, "scheduleBuilders must not be null"));
        this.minimumPaymentPolicy = Objects.requireNonNull(minimumPaymentPolicy, "minimumPaymentPolicy must not be null");
    }

    private static List<ScheduleBuilder> createDefaultBuilders(CreditTerms terms) {
        var interestCalculator = new DailyAccrualInterestCalculator(terms);
        var minPaymentPolicy = new RevolutMinimumPaymentPolicy(terms);
        var solver = new BinarySearchPaymentSolver(interestCalculator, minPaymentPolicy);

        return List.of(
                new GracePeriodScheduleBuilder(minPaymentPolicy, terms),
                new InstallmentScheduleBuilder(interestCalculator, minPaymentPolicy, solver, terms)
        );
    }

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

        return scheduleBuilders.stream()
                .filter(builder -> builder.supports(numberOfMonths))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported repayment duration: " + numberOfMonths))
                .buildSchedule(spendAmount, purchaseDate, numberOfMonths);
    }

    /**
     * Minimum payment: delegated to configured MinimumPaymentPolicy.
     */
    public BigDecimal calculateMinimumPayment(BigDecimal balance) {
        return minimumPaymentPolicy.calculateMinimumPayment(balance);
    }
}
