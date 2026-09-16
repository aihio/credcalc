# AGENTS.md

Guidance and standards for AI coding agents operating in `credcalc`.

## Project Overview

CLI credit calculator conforming to Revolut credit card terms:
- Fixed 14% annual interest rate.
- Daily simple interest accrual using dynamic days-in-year divisor (`Year.of(year).length()`, 365 or 366).
- Interest-free grace period: 1 month (full payment month after purchase) or 2 months (voluntary split payment across purchase month and statement due month).
- Installment schedules (>2 months): grace period lost, retroactive pre-billing interest from purchase date added into first billing statement.
- Minimum payment policy: `max(5% of opening balance, 5.00 EUR)`, capped at balance.
- Binary search equal payment amortization that automatically respects minimum payment floors.

## Mandatory Java & Coding Standards

1. **`var` Keyword Usage (Mandatory)**:
   - Local variable type inference (`var`) **MUST** be used for all local variable declarations where permitted.
   - Do not use explicit types for local variables unless required by the compiler (e.g., uninitialized variables, lambda targets).
2. **Java 25 Toolchain**:
   - Code against Java 25 toolchain configured in `build.gradle.kts`.
   - Use stable language features only (no preview flags).
   - Use unnamed variables (`_`) for unused exception variables in `catch` blocks.
   - Prefer Java records for immutable data carriers (`MonthlyStatement`, `CreditTerms`).
3. **Architecture & SOLID Principles**:
   - Clean separation of concerns with domain interfaces:
     - `CreditTerms`: pricing and terms configuration record.
     - `interest.InterestCalculator`: interface for interest accrual calculation (`DailyAccrualInterestCalculator`).
     - `payment.MinimumPaymentPolicy`: interface for minimum payment rules (`RevolutMinimumPaymentPolicy`).
     - `payment.PaymentSolver`: interface for equal payment discovery (`BinarySearchPaymentSolver`).
     - `schedule.ScheduleBuilder`: strategy interface for schedule creation (`GracePeriodScheduleBuilder`, `InstallmentScheduleBuilder`).
     - `CreditCalculator`: high-level facade orchestrating builders and policies.
     - `CreditCalculatorApp`: CLI entry point and dependency wiring root.
   - Depend on abstractions (interfaces), inject dependencies via constructors.

## Verification & Commands

Run these Gradle commands to verify changes:

```bash
# Compile
./gradlew compileJava

# Run single test class
./gradlew test --tests org.example.interest.DailyAccrualInterestCalculatorTest

# Run all tests
./gradlew test

# Full clean verification (compilation, resources, tests, checks)
./gradlew clean check

# Run CLI application interactively or via pipe
printf "1700\n2026-10-01\n12\n" | ./gradlew run -q --console=plain
```

## Testing Protocol

- Use JUnit Jupiter 6 (`@Test`, `org.junit.jupiter.api.Assertions.*`).
- When introducing new features or refactorings, practice Test-Driven Development (TDD).
- Add isolated unit tests for new components at public seams.
- Maintain full backwards compatibility for `CreditCalculatorTest` and CLI input/output formats.

## Git Protocol

- Do not commit changes unless explicitly requested by the user.
- Keep commits focused with descriptive conventional commit messages (`feat:`, `refactor:`, `fix:`, `test:`).
