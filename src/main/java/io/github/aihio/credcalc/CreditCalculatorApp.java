import io.github.aihio.credcalc.CreditCalculator;
import io.github.aihio.credcalc.CreditCalculatorCli;
import java.util.Scanner;

void main() {
    new CreditCalculatorCli(CreditCalculator::new, new Scanner(System.in)).run();
}
