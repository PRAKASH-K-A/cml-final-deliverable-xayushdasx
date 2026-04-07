import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory order book for a single symbol.
 * Holds bids and asks sorted by price-time priority.
 *
 * Bids: sorted descending (highest price = best bid at top)
 * Asks: sorted ascending  (lowest price  = best ask at top)
 *
 * ConcurrentSkipListMap is used because:
 *  - It keeps prices sorted automatically (no manual re-sort needed)
 *  - It is thread-safe for concurrent reads/writes from the FIX engine thread
 *
 * Matching logic will be added in Lab 7.
 */
public class OrderBook {

    private final String symbol;

    // Bids: Descending – best bid (highest price) is first key
    private final ConcurrentSkipListMap<Double, List<Order>> bids =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());

    // Asks: Ascending – best ask (lowest price) is first key
    private final ConcurrentSkipListMap<Double, List<Order>> asks =
            new ConcurrentSkipListMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() { return symbol; }

    public ConcurrentSkipListMap<Double, List<Order>> getBids() { return bids; }
    public ConcurrentSkipListMap<Double, List<Order>> getAsks() { return asks; }

    // ── Matching Logic (Lab 7) ────────────────────────────────────────────────

    public synchronized List<Execution> match(Order incoming) {
        List<Execution> executions = new java.util.ArrayList<>();
        if (incoming.getSide() == '1') { // Buy Order
            // Attempt to match against the Asks (Sellers)
            matchOrder(incoming, asks, executions);
        } else { // Sell Order
            // Attempt to match against the Bids (Buyers)
            matchOrder(incoming, bids, executions);
        }
        // If the order is not fully filled after matching, add the remainder to the book
        if (incoming.getQuantity() > 0) {
            addToBook(incoming);
        }
        return executions;
    }

    private void addToBook(Order order) {
        java.util.NavigableMap<Double, List<Order>> side = (order.getSide() == '1') ? bids : asks;
        // ComputeIfAbsent creates the list if this is the first order at this price
        side.computeIfAbsent(order.getPrice(), k -> new java.util.LinkedList<>()).add(order);
    }

    private void matchOrder(Order incoming, java.util.NavigableMap<Double, List<Order>> oppositeSide, List<Execution> trades) {
        // Continue loop while:
        // 1. The incoming order still needs to be filled (Qty > 0)
        // 2. The opposite book is not empty (There is someone to trade with)
        while (incoming.getQuantity() > 0 && !oppositeSide.isEmpty()) {
            // Peek at the best available price on the other side
            Double bestPrice = oppositeSide.firstKey();

            // Check Price Logic: Does the limit price allow this trade?
            boolean isBuy = (incoming.getSide() == '1');
            // If Buying: We want to buy Low. If BestAsk > MyLimit, I can't afford it. Stop.
            if (isBuy && incoming.getPrice() < bestPrice) break;
            // If Selling: We want to sell High. If BestBid < MyLimit, they aren't paying enough. Stop.
            if (!isBuy && incoming.getPrice() > bestPrice) break;

            // If we are here, a match is possible!
            // Get the list of orders at this price level
            List<Order> ordersAtLevel = oppositeSide.get(bestPrice);
            // Match against the first order in the list (Time Priority / FIFO)
            Order resting = ordersAtLevel.get(0);

            // Calculate Trade Quantity: The max we can trade is the smaller of the two sizes
            double tradeQty = Math.min(incoming.getQuantity(), resting.getQuantity());

            // Create Execution Record.
            // CRITICAL: The trade happens at the RESTING order's price, not the aggressor's price.
            Execution exec = new Execution(incoming, resting, tradeQty, bestPrice);
            trades.add(exec);

            // Update Order Objects (Decrement Qty) in memory
            incoming.reduceQty(tradeQty);
            resting.reduceQty(tradeQty);

            // Cleanup: remove filled orders from the book to keep the map clean.
            if (resting.getQuantity() == 0) {
                ordersAtLevel.remove(0); // Remove the filled order from the list
                // If that was the last order at that price, remove the price level entirely
                if (ordersAtLevel.isEmpty()) {
                    oppositeSide.remove(bestPrice);
                }
            }
        }
    }
}
