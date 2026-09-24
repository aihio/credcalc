package io.github.aihio.credcalc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditCalculatorCliTest {

    @Test
    void run_customPlanPrintsCustomTermsAndDueDate() {
        var output = new ByteArrayOutputStream();
        var originalOutput = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

        try {
            var input = String.join("\n", "custom", "Travel card", "USD", "20", "3", "10", "20", "15", "1000", "2025-01-10", "1");
            new CreditCalculatorCli(CreditCalculator::new, new Scanner(input)).run();
        } finally {
            System.setOut(originalOutput);
        }

        var text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Travel card Payment Schedule"));
        assertTrue(text.contains("Annual Rate: 20.00%"));
        assertTrue(text.contains("2025-02-15"));
        assertTrue(text.contains("USD"));
    }

    @Test
    void run_legacyInputUsesRevolutPlan() {
        var output = new ByteArrayOutputStream();
        var originalOutput = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

        try {
            new CreditCalculatorCli(CreditCalculator::new, new Scanner("1700\n2026-10-01\n12\n")).run();
        } finally {
            System.setOut(originalOutput);
        }

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Revolut Credit Card Payment Schedule"));
    }
}
