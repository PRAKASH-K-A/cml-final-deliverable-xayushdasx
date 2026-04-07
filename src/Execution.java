import java.util.UUID;

public class Execution {
    private final String execId;
    private final Order incoming;
    private final Order resting;
    private final double quantity;
    private final double price;

    public Execution(Order incoming, Order resting, double quantity, double price) {
        this.execId = UUID.randomUUID().toString();
        this.incoming = incoming;
        this.resting = resting;
        this.quantity = quantity;
        this.price = price;
    }

    public String getExecId() { return execId; }
    public Order getIncoming() { return incoming; }
    public Order getResting() { return resting; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public String getSymbol() { return incoming.getSymbol(); }
    public char getAggressorSide() { return incoming.getSide(); }

    @Override
    public String toString() {
        return String.format("Execution{id=%s, symbol=%s, qty=%.0f, price=%.2f}",
                execId, getSymbol(), quantity, price);
    }
}
