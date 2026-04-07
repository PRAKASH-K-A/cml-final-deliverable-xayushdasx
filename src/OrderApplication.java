import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.Reject;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * QuickFIX/J Application callback handler.
 *
 * Critical-path: parse → validate symbol → send ACK or Reject → offer to queue
 * Slow-path:     OrderPersister drains queue → INSERT into MySQL
 */
public class OrderApplication implements Application {

    private static final boolean VERBOSE_LOGGING = Boolean.parseBoolean(System.getProperty("oms.verbose", "false"));
    private static final double OPTION_STRIKE = 100.0;
    private static final double OPTION_RATE = 0.05;
    private static final double OPTION_VOLATILITY = 0.20;
    private static final double OPTION_TIME_TO_EXPIRY = 1.0;
    private final BlockingQueue<Object> dbQueue;
    private final OrderBroadcaster broadcaster;

    // Security master preloaded at startup for O(1) symbol validation
    private final Map<String, Security> validSecurities = new HashMap<>();

    // Order books per symbol
    private final Map<String, OrderBook> orderBooks = new java.util.concurrent.ConcurrentHashMap<>();

    public OrderApplication(BlockingQueue<Object> dbQueue, OrderBroadcaster broadcaster) {
        this.dbQueue = dbQueue;
        this.broadcaster = broadcaster;
    }

    // ── Application interface ─────────────────────────────────────────────────

    @Override
    public void onLogon(SessionID sessionID) {
        // Load security master from DB once when a client connects
        validSecurities.clear();
        validSecurities.putAll(DatabaseManager.loadSecurityMaster());
        System.out.println("[OrderApplication] Client logged on: " + sessionID);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        long ingressTime = System.nanoTime();

        String msgType = message.getHeader().getString(MsgType.FIELD);

        if (MsgType.ORDER_SINGLE.equals(msgType)) {
            processNewOrder(message, sessionID, ingressTime);
        }
    }

    // ── Order processing ──────────────────────────────────────────────────────

    private void processNewOrder(Message message, SessionID sessionID, long ingressTime) {
        try {
            // Parse symbol first — validate before touching any other field
            String symbol = message.getString(Symbol.FIELD);    // Tag 55

            // ── Security Master Validation ────────────────────────────────────
            if (!validSecurities.containsKey(symbol)) {
                System.out.println("[OrderApplication] REJECTED unknown symbol: " + symbol);
                sendReject(message, sessionID, "Unknown Security Symbol: " + symbol);
                return;
            }

            String clOrdID  = message.getString(ClOrdID.FIELD);   // Tag 11
            char   side     = message.getChar(Side.FIELD);        // Tag 54
            double price    = message.getDouble(Price.FIELD);     // Tag 44
            double quantity = message.getDouble(OrderQty.FIELD);  // Tag 38

            Order order = new Order(clOrdID, symbol, side, price, quantity);
            if (VERBOSE_LOGGING) {
                System.out.println("[OrderApplication] Received: " + order);
            }

            // Route to symbol book and match (Lab 7)
            OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook(k));
            java.util.List<Execution> executions = book.match(order);
            double cumulativeQty = 0;
            double cumulativeNotional = 0;
            for (Execution exec : executions) {
                if (VERBOSE_LOGGING) {
                    System.out.printf("TRADE EXECUTED: %s %.0f shares @ $%.2f%n",
                            symbol, exec.getQuantity(), exec.getPrice());
                }
                cumulativeQty += exec.getQuantity();
                cumulativeNotional += exec.getQuantity() * exec.getPrice();
                dbQueue.offer(exec);
                if (broadcaster != null) {
                    broadcaster.broadcastTrade(exec, cumulativeQty, cumulativeNotional / cumulativeQty);
                    broadcaster.broadcastOrder(exec.getResting());
                    broadcastOptionUpdate(exec);
                }
                sendFillReport(exec.getIncoming(), tradeExecId(exec), exec, sessionID,
                        cumulativeQty, cumulativeNotional / cumulativeQty, ingressTime);
                sendFillReport(exec.getResting(), tradeExecId(exec) + "-REST", exec, sessionID,
                        exec.getResting().getFilledQuantity(), exec.getPrice(), ingressTime);
                dbQueue.offer(exec.getResting());
            }

            if (broadcaster != null) {
                broadcaster.broadcastOrder(order);
            }

            if (executions.isEmpty()) {
                sendRestingOrderReport(order, sessionID, ingressTime);
            }

            boolean queued = dbQueue.offer(order);
            if (!queued) {
                System.err.println("[OrderApplication] WARNING: DB queue full! Order "
                                   + clOrdID + " was NOT queued.");
            }

        } catch (FieldNotFound e) {
            System.err.println("[OrderApplication] Missing required field: " + e.getMessage());
        }
    }

    // ── FIX message senders ───────────────────────────────────────────────────

    private void sendRestingOrderReport(Order order, SessionID sessionID, long ingressTime) {
        try {
            ExecutionReport report = new ExecutionReport(
                new OrderID(order.getOrderId()),
                new ExecID(java.util.UUID.randomUUID().toString()),
                new ExecType(ExecType.NEW),
                new OrdStatus(OrdStatus.NEW),
                new Side(order.getSide()),
                new LeavesQty(order.getQuantity()),
                new CumQty(0),
                new AvgPx(0)
            );
            report.set(new ClOrdID(order.getClOrdID()));
            report.set(new Symbol(order.getSymbol()));
            report.set(new OrderQty(order.getOriginalQuantity()));
            report.set(new Price(order.getPrice()));
            report.set(new TransactTime(LocalDateTime.now()));

            Session.sendToTarget(report, sessionID);
            recordResponseLatency(ingressTime, "NEW_ACK");
            if (VERBOSE_LOGGING) {
                System.out.println("[OrderApplication] ACK sent for: " + order.getClOrdID());
            }

        } catch (SessionNotFound e) {
            System.err.println("[OrderApplication] Session not found: " + e.getMessage());
        }
    }

    private void sendFillReport(Order order, String execId, Execution trade, SessionID sessionID,
                                double cumQty, double avgPx, long ingressTime) {
        try {
            double leavesQty = order.getQuantity();
            char ordStatus = leavesQty == 0 ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED;

            ExecutionReport fill = new ExecutionReport(
                new OrderID(order.getOrderId()),
                new ExecID(execId),
                new ExecType(ExecType.TRADE),
                new OrdStatus(ordStatus),
                new Side(order.getSide()),
                new LeavesQty(leavesQty),
                new CumQty(cumQty),
                new AvgPx(avgPx)
            );
            fill.set(new ClOrdID(order.getClOrdID()));
            fill.set(new Symbol(order.getSymbol()));
            fill.set(new OrderQty(order.getOriginalQuantity()));
            fill.set(new Price(order.getPrice()));
            fill.set(new LastPx(trade.getPrice()));
            fill.set(new LastQty(trade.getQuantity()));
            fill.set(new TransactTime(LocalDateTime.now()));

            Session.sendToTarget(fill, sessionID);
            recordResponseLatency(ingressTime, "TRADE");
            if (VERBOSE_LOGGING) {
                System.out.println("[OrderApplication] Fill report sent for: " + order.getClOrdID()
                                   + " | execId=" + execId);
            }

        } catch (SessionNotFound e) {
            System.err.println("[OrderApplication] Session not found for fill report: " + e.getMessage());
        }
    }

    private String tradeExecId(Execution trade) {
        return trade.getExecId();
    }

    private void broadcastOptionUpdate(Execution trade) {
        double spotPrice = trade.getPrice();
        double callPrice = BlackScholes.callPrice(
                spotPrice, OPTION_STRIKE, OPTION_TIME_TO_EXPIRY, OPTION_RATE, OPTION_VOLATILITY
        );
        double putPrice = BlackScholes.putPrice(
                spotPrice, OPTION_STRIKE, OPTION_TIME_TO_EXPIRY, OPTION_RATE, OPTION_VOLATILITY
        );
        String underlying = trade.getSymbol();
        String optionSymbol = underlying + "_JAN_" + (int) OPTION_STRIKE + "_CALL";

        broadcaster.broadcastOptionUpdate(
                optionSymbol,
                underlying,
                spotPrice,
                OPTION_STRIKE,
                callPrice,
                putPrice,
                OPTION_RATE,
                OPTION_VOLATILITY,
                OPTION_TIME_TO_EXPIRY
        );
    }

    private void recordResponseLatency(long ingressTime, String category) {
        long egressTime = System.nanoTime();
        PerformanceMonitor.recordLatency(egressTime - ingressTime, category);
    }

    /**
     * Sends a FIX 4.4 Reject (MsgType=3) for orders that fail validation.
     */
    private void sendReject(Message message, SessionID sessionID, String reason) {
        try {
            String refSeqNum = message.getHeader().getString(MsgSeqNum.FIELD);
            Reject reject = new Reject(new RefSeqNum(Integer.parseInt(refSeqNum)));
            reject.set(new Text(reason));
            Session.sendToTarget(reject, sessionID);
        } catch (Exception e) {
            System.err.println("[OrderApplication] Failed to send reject: " + e.getMessage());
        }
    }

    // ── Unused lifecycle callbacks ────────────────────────────────────────────

    @Override public void onCreate(SessionID sessionID) {
        System.out.println("[OrderApplication] Session created: " + sessionID);
    }

    @Override public void onLogout(SessionID sessionID) {
        System.out.println("[OrderApplication] Client logged out: " + sessionID);
    }

    @Override public void toAdmin(Message message, SessionID sessionID) {}

    @Override public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}

    @Override public void toApp(Message message, SessionID sessionID) throws DoNotSend {}
}
