package org.example.schedule;

import org.example.MonthlyStatement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Builds a monthly payment schedule for a credit transaction.
 */
public interface ScheduleBuilder {

    /**
     * Checks if this builder supports the requested repayment duration.
     */
    boolean supports(int numberOfMonths);

    /**
     * Builds the list of monthly statements.
     */
    List<MonthlyStatement> buildSchedule(BigDecimal spendAmount, LocalDate purchaseDate, int numberOfMonths);
}
