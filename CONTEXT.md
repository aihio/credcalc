# Credit Calculator Context

## Purpose

This CLI calculates payment schedules for credit card spending. Revolut Credit Card is the built-in plan. Users can enter a custom plan for one calculation run.

## Glossary

- **Credit terms**: A named card plan with currency, annual rate, minimum-payment policy, statement day, and due day.
- **Purchase date**: Date on which the card spend occurs.
- **Statement date**: First configured statement day on or after the purchase date.
- **Due date**: Payment deadline in the month after the statement date.
- **Grace period**: Interest-free repayment when the balance is paid in one month, or split between the purchase month and due month.
- **Installment schedule**: Repayment over more than two months. Interest accrues from purchase through statement date, then through each due date.
- **Monthly statement**: One scheduled payment, with opening balance, interest, payment, closing balance, and minimum payment.

## Rules

- Interest uses daily simple accrual and the actual 365- or 366-day year.
- One-month repayment has no interest.
- Two-month repayment has no interest. First payment is voluntary during the purchase month; second is due on the configured due date.
- Repayment over two months loses the grace period. Purchase-to-statement interest is included in the first statement.
- Minimum payment is the greater of a percentage of opening balance or a fixed floor, capped at balance.
- Equal installment payments are found by binary search and cannot fall below the minimum payment.

## Boundaries

- `CreditTerms` owns plan values and derives statement and due dates.
- `CreditCalculator` builds schedules from terms.
- `CreditCalculatorCli` owns terminal input and output.
- `CreditCalculatorApp.java` is the Java 26 compact source-file entry point.
- Custom plans are not saved between runs.

## Compatibility

- Numeric first input selects the Revolut plan for legacy piped usage.
- `custom` as first input starts custom-plan entry.
