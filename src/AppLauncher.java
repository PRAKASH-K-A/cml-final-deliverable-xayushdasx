import quickfix.*;

import java.io.FileInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Entry point for the Order Management System server.
 *
 * Wiring order:
 *   1. Create the shared, unbounded queue.
 *   2. Start the OrderPersister worker on its own daemon thread.
 *   3. Pass the queue to OrderApplication so it can offer() orders.
 *   4. Start the QuickFIX/J acceptor to listen for inbound FIX connections.
 */
public class AppLauncher {

    public static void main(String[] args) throws Exception {

        // ── 1. Shared queue between FIX thread (producer) and DB thread (consumer) ──
        BlockingQueue<Object> dbQueue = new LinkedBlockingQueue<>();
        // LinkedBlockingQueue is unbounded: offer() never blocks/rejects,
        // so the FIX engine is never stalled even if MySQL goes offline.

        // ── 2. Start the async persistence worker ─────────────────────────────────
        OrderPersister persister = new OrderPersister(dbQueue);
        Thread persisterThread = new Thread(persister, "db-writer");
        persisterThread.setDaemon(true); // Exits automatically when main thread exits
        persisterThread.start();

        // ── 3. Create the FIX application with a reference to the queue ──────────
        boolean websocketEnabled = Boolean.parseBoolean(System.getProperty("oms.ws.enabled", "true"));
        int websocketPort = Integer.parseInt(System.getProperty("oms.ws.port", "8080"));
        OrderBroadcaster broadcaster = null;
        if (websocketEnabled) {
            broadcaster = new OrderBroadcaster(websocketPort);
            broadcaster.start();
        } else {
            System.out.println("[AppLauncher] WebSocket broadcaster disabled for this run.");
        }
        OrderApplication application = new OrderApplication(dbQueue, broadcaster);

        // ── 4. Load FIX session config and start the acceptor ────────────────────
        String configFile = args.length > 0 ? args[0] : "config/order-service.cfg";
        SessionSettings   settings     = new SessionSettings(new FileInputStream(configFile));
        MessageStoreFactory storeFactory = new MemoryStoreFactory();
        LogFactory          logFactory   = new FileLogFactory(settings);
        MessageFactory      msgFactory   = new DefaultMessageFactory();

        Acceptor acceptor = new SocketAcceptor(
                application, storeFactory, settings, logFactory, msgFactory);

        acceptor.start();
        System.out.println("[AppLauncher] FIX acceptor started. Waiting for connections...");
        boolean waitForConsoleInput = Boolean.parseBoolean(System.getProperty("oms.wait.for.stdin", "true"));
        if (waitForConsoleInput) {
            System.out.println("[AppLauncher] Press <Enter> to shut down.");
            System.in.read();
        } else {
            System.out.println("[AppLauncher] Running in headless mode. Stop the process to shut down.");
            while (true) {
                Thread.sleep(1000);
            }
        }

        // ── 5. Graceful shutdown ──────────────────────────────────────────────────
        System.out.println("[AppLauncher] Shutting down...");
        PerformanceMonitor.printFinalSummary();
        acceptor.stop();
        if (broadcaster != null) {
            broadcaster.stop();
        }
        persister.stop();
        persisterThread.interrupt(); // Unblock queue.take() if idle
    }
}
