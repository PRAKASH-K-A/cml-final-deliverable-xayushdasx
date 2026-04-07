import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

public class OrderBroadcaster extends WebSocketServer {

    private final Gson gson = new Gson();

    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[OrderBroadcaster] UI connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[OrderBroadcaster] UI disconnected: " + conn.getRemoteSocketAddress()
                           + " | code=" + code + " | reason=" + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[OrderBroadcaster] Ignoring inbound UI message: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[OrderBroadcaster] WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[OrderBroadcaster] WebSocket server started on port " + getPort());
    }

    public void broadcastOrder(Order order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("clOrdID", order.getClOrdID());
        data.put("symbol", order.getSymbol());
        data.put("side", String.valueOf(order.getSide()));
        data.put("price", order.getPrice());
        data.put("quantity", order.getOriginalQuantity());
        data.put("leavesQty", order.getQuantity());
        data.put("filledQty", order.getFilledQuantity());
        data.put("status", determineOrderStatus(order));
        data.put("timestamp", System.currentTimeMillis());
        broadcastEnvelope("ORDER", data);
    }

    public void broadcastTrade(Execution execution, double cumulativeQty, double avgPx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("execId", execution.getExecId());
        data.put("symbol", execution.getSymbol());
        data.put("side", String.valueOf(execution.getAggressorSide()));
        data.put("price", execution.getPrice());
        data.put("quantity", execution.getQuantity());
        data.put("cumQty", cumulativeQty);
        data.put("avgPx", avgPx);
        data.put("incomingOrderId", execution.getIncoming().getOrderId());
        data.put("incomingClOrdID", execution.getIncoming().getClOrdID());
        data.put("restingOrderId", execution.getResting().getOrderId());
        data.put("restingClOrdID", execution.getResting().getClOrdID());
        data.put("timestamp", System.currentTimeMillis());
        broadcastEnvelope("TRADE", data);
    }

    public void broadcastOptionUpdate(String optionSymbol, String underlyingSymbol, double spotPrice,
                                      double strike, double callPrice, double putPrice,
                                      double rate, double volatility, double timeToExpiry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("optionSymbol", optionSymbol);
        data.put("underlyingSymbol", underlyingSymbol);
        data.put("spotPrice", spotPrice);
        data.put("strike", strike);
        data.put("callPrice", callPrice);
        data.put("putPrice", putPrice);
        data.put("rate", rate);
        data.put("volatility", volatility);
        data.put("timeToExpiry", timeToExpiry);
        data.put("timestamp", System.currentTimeMillis());
        broadcastEnvelope("OPTION_UPDATE", data);
    }

    private void broadcastEnvelope(String type, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("data", data);
        broadcast(gson.toJson(envelope));
    }

    private String determineOrderStatus(Order order) {
        if (order.isFilled()) {
            return "FILLED";
        }
        if (order.getFilledQuantity() > 0) {
            return "PARTIALLY_FILLED";
        }
        return "NEW";
    }
}
