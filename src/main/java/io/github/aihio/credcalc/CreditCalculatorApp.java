package io.github.aihio.credcalc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/**
 * CLI entry point for the credit card payment schedule calculator.
 * Reads spend amount, purchase date, and repayment months from stdin.
 * Prints a formatted payment schedule table.
 */
public class CreditCalculatorApp {

    private final Function<CreditTerms, CreditCalculator> calculatorFactory;
    private final Scanner scanner;

    public CreditCalculatorApp(Function<CreditTerms, CreditCalculator> calculatorFactory, Scanner scanner) {
        this.calculatorFactory = calculatorFactory;
        this.scanner = scanner;
    }

    static void main() {
        var app = new CreditCalculatorApp(CreditCalculator::new, new Scanner(System.in));
        app.run();
    }

    public void run() {
        System.out.print("Enter credit amount (EUR), or custom for another plan: ");
        var selection = selectPlan(scanner, scanner.nextLine().trim());
        var terms = selection.terms();
        var calculator = calculatorFactory.apply(terms);
        var spendAmount = readSpendAmount(scanner, terms.currency(), selection.spendInput());
        var purchaseDate = readPurchaseDate(scanner);
        var numberOfMonths = readNumberOfMonths(scanner);

        try {
            var schedule = calculator.calculatePaymentSchedule(
                    spendAmount, purchaseDate, numberOfMonths);
            printSchedule(schedule, spendAmount, terms);
        } catch (IllegalArgumentException _) {
            System.err.println("Error: Invalid credit terms");
            System.exit(1);
        }
    }

    private static PlanSelection selectPlan(Scanner scanner, String firstInput) {
        if (firstInput.equalsIgnoreCase("custom")) {
            return new PlanSelection(readCustomTerms(scanner), null);
        }
        return new PlanSelection(CreditTerms.revolut(), firstInput);
    }

    private static CreditTerms readCustomTerms(Scanner scanner) {
        var name = readText(scanner, "Enter plan name: ");
        var currency = readText(scanner, "Enter currency code: ").toUpperCase(java.util.Locale.ROOT);
        var annualRate = readNonNegativeDecimal(scanner, "Enter annual rate (%): ").movePointLeft(2);
        var minimumPaymentPercent = readNonNegativeDecimal(scanner, "Enter minimum payment (%): ").movePointLeft(2);
        var minimumPaymentFloor = readNonNegativeDecimal(scanner, "Enter minimum payment floor: ");
        var statementDay = readDayOfMonth(scanner, "Enter statement day (1-31): ");
        var paymentDueDay = readDayOfMonth(scanner, "Enter payment due day (1-31): ");
        return new CreditTerms(name, currency, annualRate, minimumPaymentPercent, minimumPaymentFloor,
                statementDay, paymentDueDay);
    }

    private static String readText(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            var value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Value must not be blank. Try again.");
        }
    }

    private static BigDecimal readNonNegativeDecimal(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                var value = new BigDecimal(scanner.nextLine().trim());
                if (value.signum() < 0) {
                    System.out.println("Value must not be negative. Try again.");
                    continue;
                }
                return value;
            } catch (NumberFormatException _) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static int readDayOfMonth(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                var day = Integer.parseInt(scanner.nextLine().trim());
                if (day >= 1 && day <= 31) {
                    return day;
                }
                System.out.println("Day must be from 1 to 31. Try again.");
            } catch (NumberFormatException _) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static BigDecimal readSpendAmount(Scanner scanner, String currency, String initialInput) {
        var input = initialInput;
        while (true) {
            System.out.print("Enter credit amount (" + currency + "): ");
            try {
                var value = input == null ? scanner.nextLine() : input;
                input = null;
                var amount = new BigDecimal(value.trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive. Try again.");
                    continue;
                }
                return amount.setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException _) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static LocalDate readPurchaseDate(Scanner scanner) {
        while (true) {
            System.out.print("Enter date of purchase (YYYY-MM-DD): ");
            try {
                return LocalDate.parse(scanner.nextLine().trim());
            } catch (DateTimeParseException _) {
                System.out.println("Invalid format. Use YYYY-MM-DD (e.g. 2025-01-15). Try again.");
            }
        }
    }

    private static int readNumberOfMonths(Scanner scanner) {
        while (true) {
            System.out.print("Enter number of months to repay: ");
            try {
                var months = Integer.parseInt(scanner.nextLine().trim());
                if (months <= 0) {
                    System.out.println("Must be positive. Try again.");
                    continue;
                }
                return months;
            } catch (NumberFormatException _) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static void printSchedule(List<MonthlyStatement> schedule, BigDecimal spendAmount, CreditTerms terms) {
        var separator = "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(12)
                + "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(14) + "+";

        System.out.println();
        System.out.println(terms.name() + " Payment Schedule");
        System.out.printf("Annual Rate: %.2f%%  |  Daily interest  |  Currency: %s%n",
                terms.annualRate().movePointRight(2), terms.currency());
        System.out.println();
        System.out.println(separator);
        System.out.printf("| %-10s | %-12s | %-10s | %-10s | %-12s | %-12s |%n",
                "Due date", "Opening", "Interest", "Payment", "Closing", "Min Payment");
        System.out.println(separator);

        var totalInterest = BigDecimal.ZERO;
        var totalPaid = BigDecimal.ZERO;

        for (var statement : schedule) {
            System.out.printf("| %-10s | %12s | %10s | %10s | %12s | %12s |%n",
                    statement.dueDate(),
                    formatAmount(statement.openingBalance()),
                    formatAmount(statement.interestCharged()),
                    formatAmount(statement.paymentAmount()),
                    formatAmount(statement.closingBalance()),
                    formatAmount(statement.minimumPayment()));

            totalInterest = totalInterest.add(statement.interestCharged());
            totalPaid = totalPaid.add(statement.paymentAmount());
        }

        System.out.println(separator);
        System.out.println();
        System.out.println("Summary:");
        System.out.printf("  Credit amount:  %s %s%n", formatAmount(spendAmount), terms.currency());
        System.out.printf("  Total interest: %s %s%n", formatAmount(totalInterest), terms.currency());
        System.out.printf("  Total paid:     %s %s%n", formatAmount(totalPaid), terms.currency());
    }

    private static String formatAmount(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private record PlanSelection(CreditTerms terms, String spendInput) {
    }
}
