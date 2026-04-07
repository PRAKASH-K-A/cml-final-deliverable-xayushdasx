import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.io.FileInputStream;
import java.time.LocalDateTime;

/**
 * FIX 4.4 Initiator (client) that sends 100 NewOrderSingle messages
 * to the server, then waits for acknowledgments.
 *
 * Run AFTER the server (AppLauncher) is already running.
 */
public class OrderClient implements Application {
    private static final boolean VERBOSE_LOGGING = Boolean.parseBoolean(System.getProperty("oms.client.verbose", "false"));
    private static final String[] SYMBOL_ROTATION = {"GOOG", "MSFT", "IBM"};

    private SessionID sessionID;
    private volatile boolean loggedOn = false;

    // ── Application callbacks ─────────────────────────────────────────────────

    @Override
    public void onLogon(SessionID sessionID) {
        this.sessionID = sessionID;
        this.loggedOn  = true;
        System.out.println("[Client] Logged on: " + sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        this.loggedOn = false;
        System.out.println("[Client] Logged out.");
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        // ExecutionReport (ACK) received from server
        try {
            String clOrdID = message.getString(ClOrdID.FIELD);
            if (VERBOSE_LOGGING) {
                System.out.println("[Client] ACK received for: " + clOrdID);
            }
        } catch (FieldNotFound ignored) {}
    }

    // ── Order sending ─────────────────────────────────────────────────────────

    public void sendOrders(int count) throws Exception {
        sendOrders(count, 100);
    }

    public void sendOrders(int count, int ratePerSecond) throws Exception {
        // Wait until the session is logged on
        int waited = 0;
        while (!loggedOn && waited < 10000) {
            Thread.sleep(200);
            waited += 200;
        }
        if (!loggedOn) {
            System.err.println("[Client] Could not log on within 10 seconds. Is the server running?");
            return;
        }

        System.out.println("[Client] Sending " + count + " orders at " + ratePerSecond + " orders/sec...");
        long start = System.currentTimeMillis();
        long spacingNanos = ratePerSecond <= 0 ? 0 : 1_000_000_000L / ratePerSecond;
        long nextSendTime = System.nanoTime();

        for (int i = 1; i <= count; i++) {
            char side = (i % 2 == 0) ? Side.BUY : Side.SELL;
            String symbol = SYMBOL_ROTATION[(i - 1) % SYMBOL_ROTATION.length];
            NewOrderSingle order = new NewOrderSingle(
                new ClOrdID("ORD-" + String.format("%04d", i)),
                new Side(side),
                new TransactTime(LocalDateTime.now()),
                new OrdType(OrdType.LIMIT)
            );
            order.set(new Symbol(symbol));
            order.set(new OrderQty(100));
            order.set(new Price(100.00 + ((i - 1) % 10)));   // bounded ladder for repeatable matching

            Session.sendToTarget(order, sessionID);

            if (spacingNanos > 0) {
                nextSendTime += spacingNanos;
                while (System.nanoTime() < nextSendTime) {
                    Thread.onSpinWait();
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[Client] All " + count + " orders sent in " + elapsed + " ms. Waiting for ACKs...");

        // Keep the session alive long enough for ACKs to arrive
        Thread.sleep(5000);
    }

    // ── Unused callbacks ──────────────────────────────────────────────────────

    @Override public void onCreate(SessionID s) {}
    @Override public void toAdmin(Message m, SessionID s) {}
    @Override public void fromAdmin(Message m, SessionID s) {}
    @Override public void toApp(Message m, SessionID s) throws DoNotSend {}

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String configFile = args.length > 0 ? args[0] : "config/client.cfg";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int ratePerSecond = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        OrderClient client = new OrderClient();

        SessionSettings    settings     = new SessionSettings(new FileInputStream(configFile));
        MessageStoreFactory storeFactory = new MemoryStoreFactory();
        LogFactory          logFactory   = new FileLogFactory(settings);
        MessageFactory      msgFactory   = new DefaultMessageFactory();

        Initiator initiator = new SocketInitiator(
                client, storeFactory, settings, logFactory, msgFactory);

        initiator.start();
        client.sendOrders(count, ratePerSecond);
        initiator.stop();

        System.out.println("[Client] Done.");
    }
}
