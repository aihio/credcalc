package io.github.aihio.credcalc.schedule;

import io.github.aihio.credcalc.CreditTerms;
import io.github.aihio.credcalc.interest.DailyAccrualInterestCalculator;
import io.github.aihio.credcalc.payment.BinarySearchPaymentSolver;
import io.github.aihio.credcalc.payment.RevolutMinimumPaymentPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class InstallmentScheduleBuilderTest {

    private final CreditTerms terms = CreditTerms.revolut();
    private final DailyAccrualInterestCalculator interestCalculator = new DailyAccrualInterestCalculator(terms);
    private final RevolutMinimumPaymentPolicy minimumPaymentPolicy = new RevolutMinimumPaymentPolicy(terms);
    private final BinarySearchPaymentSolver paymentSolver = new BinarySearchPaymentSolver(interestCalculator, minimumPaymentPolicy);

    private final ScheduleBuilder builder = new InstallmentScheduleBuilder(
            interestCalculator, minimumPaymentPolicy, paymentSolver
    );

    @Test
    void supports_threeOrMoreMonths_returnsTrue() {
        assertFalse(builder.supports(1));
        assertFalse(builder.supports(2));
        assertTrue(builder.supports(3));
        assertTrue(builder.supports(12));
    }

    @Test
    void buildSchedule_threeMonths_correctScheduleAndZeroFinalBalance() {
        var schedule = builder.buildSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                3
        );

        assertEquals(3, schedule.size());
        var feb = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), feb.month());
        assertEquals(new BigDecimal("1011.89"), feb.openingBalance());
        assertEquals(new BigDecimal("22.76"), feb.interestCharged());
        assertEquals(0, schedule.getLast().closingBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void buildSchedule_unsupportedDuration_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 1),
                2
        ));
    }
}
