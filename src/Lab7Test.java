import java.util.List;

public class Lab7Test {
    public static void main(String[] args) {
        OrderBook book = new OrderBook("MSFT");
        
        System.out.println("--- Scenario: Assessment Trace ---");
        // 1. Sell 100 @ 50.00
        Order sell1 = new Order("O-1", "MSFT", '2', 50.00, 100);
        System.out.println("Submitting: " + sell1);
        List<Execution> exec1 = book.match(sell1);
        printExecutions(exec1);

        // 2. Sell 100 @ 51.00
        Order sell2 = new Order("O-2", "MSFT", '2', 51.00, 100);
        System.out.println("Submitting: " + sell2);
        List<Execution> exec2 = book.match(sell2);
        printExecutions(exec2);
        
        // 3. Buy 150 @ 52.00
        Order buy1 = new Order("O-3", "MSFT", '1', 52.00, 150);
        System.out.println("Submitting: " + buy1);
        List<Execution> exec3 = book.match(buy1);
        printExecutions(exec3);

        System.out.println("Remaining Bids in book: " + book.getBids());
        System.out.println("Remaining Asks in book: " + book.getAsks());
    }

    private static void printExecutions(List<Execution> executions) {
        for (int i = 0; i < executions.size(); i++) {
            Execution e = executions.get(i);
            System.out.printf("TRADE EXECUTED: %s %.0f shares @ $%.2f%n", 
                e.getIncoming().getSymbol(), 
                e.getQuantity(), 
                e.getPrice());
        }
    }
}
