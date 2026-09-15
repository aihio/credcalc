package org.example;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * CLI entry point for the credit card payment schedule calculator.
 * Reads spend amount, spend month, and repayment months from stdin.
 * Prints a formatted payment schedule table.
 */
public class CreditCalculatorApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        CreditCalculator calculator = new CreditCalculator();

        BigDecimal spendAmount = readSpendAmount(scanner);
        YearMonth spendMonth = readSpendMonth(scanner);
        int numberOfMonths = readNumberOfMonths(scanner);

        try {
            List<MonthlyStatement> schedule = calculator.calculatePaymentSchedule(
                    spendAmount, spendMonth, numberOfMonths);
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
                BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive. Try again.");
                    continue;
                }
                return amount.setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static YearMonth readSpendMonth(Scanner scanner) {
        while (true) {
            System.out.print("Enter month of purchase (YYYY-MM): ");
            try {
                return YearMonth.parse(scanner.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println("Invalid format. Use YYYY-MM (e.g. 2025-01). Try again.");
            }
        }
    }

    private static int readNumberOfMonths(Scanner scanner) {
        while (true) {
            System.out.print("Enter number of months to repay: ");
            try {
                int months = Integer.parseInt(scanner.nextLine().trim());
                if (months <= 0) {
                    System.out.println("Must be positive. Try again.");
                    continue;
                }
                return months;
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static void printSchedule(List<MonthlyStatement> schedule, BigDecimal spendAmount) {
        String separator = "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(12)
                + "+" + "-".repeat(12) + "+" + "-".repeat(14) + "+" + "-".repeat(14) + "+";

        System.out.println();
        System.out.println("Credit Card Payment Schedule");
        System.out.println("Annual Rate: 14.00%  |  Daily Rate: 0.14/365");
        System.out.println();
        System.out.println(separator);
        System.out.printf("| %-10s | %-12s | %-10s | %-10s | %-12s | %-12s |%n",
                "Month", "Opening", "Interest", "Payment", "Closing", "Min Payment");
        System.out.println(separator);

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (MonthlyStatement statement : schedule) {
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
