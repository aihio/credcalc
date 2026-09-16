package io.github.aihio.credcalc.interest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Calculates interest accrued on outstanding balances.
 */
public interface InterestCalculator {

    /**
     * Pre-billing interest from purchase date to end of purchase month.
     */
    BigDecimal calculatePreBillingInterest(BigDecimal balance, LocalDate purchaseDate);

    /**
     * Interest accrued over a full billing month.
     */
    BigDecimal calculateMonthInterest(BigDecimal balance, YearMonth month);
}
