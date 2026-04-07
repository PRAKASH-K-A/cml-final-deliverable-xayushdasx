import java.util.UUID;

/**
 * POJO representing a single order received over the FIX protocol.
 * Populated by OrderApplication when a NewOrderSingle (MsgType=D) arrives.
 */
public class Order {

    private final String orderId;   // Server-generated unique ID
    private final String clOrdID;   // Client Order ID  (FIX Tag 11)
    private final String symbol;    // Instrument symbol (FIX Tag 55)
    private final char   side;      // 1=Buy, 2=Sell    (FIX Tag 54)
    private final double price;     // Limit price       (FIX Tag 44)
    private final double originalQuantity;
    private double quantity;        // Remaining qty     (FIX Tag 38)

    public Order(String clOrdID, String symbol, char side, double price, double quantity) {
        this.orderId   = UUID.randomUUID().toString();
        this.clOrdID   = clOrdID;
        this.symbol    = symbol;
        this.side      = side;
        this.price     = price;
        this.originalQuantity = quantity;
        this.quantity  = quantity;
    }

    public void reduceQty(double amount) {
        if (amount > this.quantity) {
            throw new IllegalArgumentException("Cannot reduce by more than current quantity");
        }
        this.quantity -= amount;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getOrderId()  { return orderId;  }
    public String getClOrdID()  { return clOrdID;  }
    public String getSymbol()   { return symbol;   }
    public char   getSide()     { return side;     }
    public double getPrice()    { return price;    }
    public double getOriginalQuantity() { return originalQuantity; }
    public double getQuantity() { return quantity; }
    public double getFilledQuantity() { return originalQuantity - quantity; }
    public boolean isFilled() { return quantity == 0; }

    @Override
    public String toString() {
        return String.format("Order{id=%s, clOrdID=%s, symbol=%s, side=%c, price=%.2f, qty=%.0f, leaves=%.0f}",
                orderId, clOrdID, symbol, side, price, originalQuantity, quantity);
    }
}
