package io.github.aihio.credcalc.schedule;

import io.github.aihio.credcalc.CreditTerms;
import io.github.aihio.credcalc.payment.RevolutMinimumPaymentPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class GracePeriodScheduleBuilderTest {

    private final ScheduleBuilder builder = new GracePeriodScheduleBuilder(
            new RevolutMinimumPaymentPolicy(CreditTerms.revolut())
    );

    @Test
    void supports_oneAndTwoMonths_returnsTrue() {
        assertTrue(builder.supports(1));
        assertTrue(builder.supports(2));
        assertFalse(builder.supports(0));
        assertFalse(builder.supports(3));
    }

    @Test
    void buildSchedule_oneMonth_singleZeroInterestStatement() {
        var schedule = builder.buildSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 15),
                1
        );

        assertEquals(1, schedule.size());
        var stmt = schedule.getFirst();
        assertEquals(YearMonth.of(2025, 2), stmt.month());
        assertEquals(new BigDecimal("1000.00"), stmt.openingBalance());
        assertEquals(new BigDecimal("0.00"), stmt.interestCharged());
        assertEquals(new BigDecimal("1000.00"), stmt.paymentAmount());
        assertEquals(new BigDecimal("0.00"), stmt.closingBalance());
    }

    @Test
    void buildSchedule_twoMonths_twoZeroInterestStatements() {
        var schedule = builder.buildSchedule(
                new BigDecimal("1600.00"),
                LocalDate.of(2025, 10, 15),
                2
        );

        assertEquals(2, schedule.size());
        var oct = schedule.get(0);
        assertEquals(YearMonth.of(2025, 10), oct.month());
        assertEquals(new BigDecimal("800.00"), oct.paymentAmount());
        assertEquals(new BigDecimal("0.00"), oct.interestCharged());

        var nov = schedule.get(1);
        assertEquals(YearMonth.of(2025, 11), nov.month());
        assertEquals(new BigDecimal("800.00"), nov.paymentAmount());
        assertEquals(new BigDecimal("0.00"), nov.interestCharged());
        assertEquals(new BigDecimal("0.00"), nov.closingBalance());
    }

    @Test
    void buildSchedule_unsupportedDuration_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildSchedule(
                new BigDecimal("1000.00"),
                LocalDate.of(2025, 1, 15),
                3
        ));
    }
}
