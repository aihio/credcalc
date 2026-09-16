package io.github.aihio.credcalc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * CLI entry point for the credit card payment schedule calculator.
 * Reads spend amount, purchase date, and repayment months from stdin.
 * Prints a formatted payment schedule table.
 */
public class CreditCalculatorApp {

    private final CreditCalculator calculator;
    private final Scanner scanner;

    public CreditCalculatorApp(CreditCalculator calculator, Scanner scanner) {
        this.calculator = calculator;
        this.scanner = scanner;
    }

    static void main() {
        var terms = CreditTerms.revolut();
        var app = new CreditCalculatorApp(new CreditCalculator(terms), new Scanner(System.in));
        app.run();
    }

    public void run() {
        var spendAmount = readSpendAmount(scanner);
        var purchaseDate = readPurchaseDate(scanner);
        var numberOfMonths = readNumberOfMonths(scanner);

        try {
            var schedule = calculator.calculatePaymentSchedule(
                    spendAmount, purchaseDate, numberOfMonths);
            printSchedule(schedule, spendAmount);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static BigDecimal readSpendAmount(Scanner scanner) {
        while (true) {
            System.out.print("Enter credit amount (EUR): ");
            try {
                var amount = new BigDecimal(scanner.nextLine().trim());
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

    private static void printSchedule(List<MonthlyStatement> schedule, BigDecimal spendAmount) {
        var separator = "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(12)
                + "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(14) + "+";

        System.out.println();
        System.out.println("Credit Card Payment Schedule");
        System.out.println("Annual Rate: 14.00%  |  Daily Rate: 0.14 / actual days in year");
        System.out.println();
        System.out.println(separator);
        System.out.printf("| %-10s | %-12s | %-10s | %-10s | %-12s | %-12s |%n",
                "Month", "Opening", "Interest", "Payment", "Closing", "Min Payment");
        System.out.println(separator);

        var totalInterest = BigDecimal.ZERO;
        var totalPaid = BigDecimal.ZERO;

        for (var statement : schedule) {
            System.out.printf("| %-10s | %12s | %10s | %10s | %12s | %12s |%n",
                    statement.month(),
                    formatEur(statement.openingBalance()),
                    formatEur(statement.interestCharged()),
                    formatEur(statement.paymentAmount()),
                    formatEur(statement.closingBalance()),
                    formatEur(statement.minimumPayment()));

            totalInterest = totalInterest.add(statement.interestCharged());
            totalPaid = totalPaid.add(statement.paymentAmount());
        }

        System.out.println(separator);
        System.out.println();
        System.out.println("Summary:");
        System.out.printf("  Credit amount:  %s EUR%n", formatEur(spendAmount));
        System.out.printf("  Total interest: %s EUR%n", formatEur(totalInterest));
        System.out.printf("  Total paid:     %s EUR%n", formatEur(totalPaid));
    }

    private static String formatEur(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
