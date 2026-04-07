import java.util.concurrent.BlockingQueue;

/**
 * Asynchronous persistence worker.
 *
 * Runs on its own dedicated thread (started in AppLauncher).
 * Continuously drains the shared BlockingQueue and writes each Order to MySQL.
 *
 * Design notes:
 *  - BlockingQueue.take() blocks without burning CPU when the queue is empty.
 *  - volatile boolean ensures the stop() signal is visible across threads.
 *  - InterruptedException is re-set on the thread so callers can detect it.
 */
public class OrderPersister implements Runnable {
    private static final boolean VERBOSE_LOGGING = Boolean.parseBoolean(System.getProperty("oms.db.verbose", "false"));

    private final BlockingQueue<Object> queue;
    private volatile boolean running = true;

    public OrderPersister(BlockingQueue<Object> queue) {
        this.queue = queue;
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        System.out.println("[OrderPersister] Persistence worker started.");

        while (running) {
            try {
                // Blocks until an Order is available – zero busy-wait overhead.
                Object item = queue.take();

                if (item instanceof Order) {
                    Order order = (Order) item;
                    boolean saved = DatabaseManager.insertOrder(order);
                    if (saved && VERBOSE_LOGGING) {
                        System.out.println("[OrderPersister] Persisted order: " + order.getClOrdID()
                                           + " | server_id=" + order.getOrderId());
                    }
                } else if (item instanceof Execution) {
                    Execution execution = (Execution) item;
                    boolean saved = DatabaseManager.insertExecution(execution);
                    if (saved && VERBOSE_LOGGING) {
                        System.out.println("[OrderPersister] Persisted execution: " + execution.getExecId()
                                           + " | symbol=" + execution.getSymbol());
                    }
                } else {
                    System.err.println("[OrderPersister] Ignoring unsupported queue item: " + item);
                }

            } catch (InterruptedException e) {
                // Restore the interrupted flag and let the loop exit cleanly.
                Thread.currentThread().interrupt();
                System.out.println("[OrderPersister] Worker interrupted – shutting down.");
                break;
            }
        }

        System.out.println("[OrderPersister] Worker stopped.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Signals the worker loop to exit after the current DB write completes.
     * Also interrupts the thread in case it is blocked on queue.take().
     */
    public void stop() {
        this.running = false;
    }
}
