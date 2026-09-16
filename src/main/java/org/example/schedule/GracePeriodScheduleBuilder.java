package org.example.schedule;

import org.example.MonthlyStatement;
import org.example.payment.MinimumPaymentPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class GracePeriodScheduleBuilder implements ScheduleBuilder {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private final MinimumPaymentPolicy minimumPaymentPolicy;

    public GracePeriodScheduleBuilder(MinimumPaymentPolicy minimumPaymentPolicy) {
        this.minimumPaymentPolicy = minimumPaymentPolicy;
    }

    @Override
    public boolean supports(int numberOfMonths) {
        return numberOfMonths == 1 || numberOfMonths == 2;
    }

    @Override
    public List<MonthlyStatement> buildSchedule(
            BigDecimal spendAmount, LocalDate purchaseDate, int numberOfMonths) {

        if (!supports(numberOfMonths)) {
            throw new IllegalArgumentException("Unsupported repayment duration: " + numberOfMonths);
        }

        var purchaseMonth = YearMonth.from(purchaseDate);

        if (numberOfMonths == 1) {
            var paymentMonth = purchaseMonth.plusMonths(1);
            var minimumPayment = minimumPaymentPolicy.calculateMinimumPayment(spendAmount);
            return List.of(new MonthlyStatement(
                    paymentMonth, spendAmount, ZERO_SCALED, spendAmount, ZERO_SCALED, minimumPayment));
        }

        // 2-month grace: split principal, enforce minimum on first payment
        var minimumFirst = minimumPaymentPolicy.calculateMinimumPayment(spendAmount);
        var halfPayment = spendAmount.divide(BigDecimal.TWO, SCALE, ROUNDING);
        var firstPayment = halfPayment.max(minimumFirst);
        var remainingBalance = spendAmount.subtract(firstPayment).setScale(SCALE, ROUNDING);
        var minimumSecond = minimumPaymentPolicy.calculateMinimumPayment(remainingBalance);

        return List.of(
                new MonthlyStatement(purchaseMonth, spendAmount, ZERO_SCALED,
                        firstPayment, remainingBalance, minimumFirst),
                new MonthlyStatement(purchaseMonth.plusMonths(1), remainingBalance, ZERO_SCALED,
                        remainingBalance, ZERO_SCALED, minimumSecond));
    }
}
