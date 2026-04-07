import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that owns the JDBC connection details and executes inserts.
 */
public class DatabaseManager {

    private static final String URL = "jdbc:mysql://localhost:3306/trading_system"
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = System.getenv("DB_PASS") != null
            ? System.getenv("DB_PASS")
            : "ayushdas@06";

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 250L;

    private static final String INSERT_SQL =
            "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "cl_ord_id = VALUES(cl_ord_id), "
                    + "symbol = VALUES(symbol), "
                    + "side = VALUES(side), "
                    + "price = VALUES(price), "
                    + "quantity = VALUES(quantity), "
                    + "status = VALUES(status)";

    private static final String INSERT_EXECUTION_SQL =
            "INSERT INTO executions (exec_id, incoming_order_id, resting_order_id, symbol, aggressor_side, price, quantity) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static Connection sharedConnection;

    private DatabaseManager() {}

    public static Map<String, Security> loadSecurityMaster() {
        Map<String, Security> map = new HashMap<>();
        String sql = "SELECT symbol, security_type, description, lot_size FROM security_master";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String symbol = rs.getString("symbol");
                String securityType = rs.getString("security_type");
                String description = rs.getString("description");
                int lotSize = rs.getInt("lot_size");
                map.put(symbol, new Security(symbol, securityType, description, lotSize));
            }
            System.out.println("[DatabaseManager] Loaded " + map.size() + " securities from security_master.");

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to load security master: " + e.getMessage());
        }

        return map;
    }

    public static boolean insertOrder(Order order) {
        return executeWithRetry(INSERT_SQL, pstmt -> {
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getOriginalQuantity());
            pstmt.setString(7, determineOrderStatus(order));
        }, "order " + order.getClOrdID());
    }

    public static boolean insertExecution(Execution execution) {
        return executeWithRetry(INSERT_EXECUTION_SQL, pstmt -> {
            pstmt.setString(1, execution.getExecId());
            pstmt.setString(2, execution.getIncoming().getOrderId());
            pstmt.setString(3, execution.getResting().getOrderId());
            pstmt.setString(4, execution.getSymbol());
            pstmt.setString(5, String.valueOf(execution.getAggressorSide()));
            pstmt.setDouble(6, execution.getPrice());
            pstmt.setDouble(7, execution.getQuantity());
        }, "execution " + execution.getExecId());
    }

    public static synchronized Connection getConnection() throws SQLException {
        try {
            if (sharedConnection == null || sharedConnection.isClosed() || !sharedConnection.isValid(2)) {
                sharedConnection = DriverManager.getConnection(URL, USER, PASS);
            }
            return sharedConnection;
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] DB connection lost. Retrying...");
            invalidateConnection();
            throw e;
        }
    }

    private static boolean executeWithRetry(String sql, SqlBinder binder, String targetLabel) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                binder.bind(pstmt);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Write failed for " + targetLabel
                        + " (attempt " + attempt + "/" + MAX_RETRIES + "): " + e.getMessage());
                invalidateConnection();

                if (attempt == MAX_RETRIES) {
                    return false;
                }

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs *= 2;
            }
        }

        return false;
    }

    private static synchronized void invalidateConnection() {
        if (sharedConnection != null) {
            try {
                sharedConnection.close();
            } catch (SQLException ignored) {
                // Ignore cleanup failure while already recovering.
            }
            sharedConnection = null;
        }
    }

    private static String determineOrderStatus(Order order) {
        if (order.isFilled()) {
            return "FILLED";
        }
        if (order.getFilledQuantity() > 0) {
            return "PARTIALLY_FILLED";
        }
        return "NEW";
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement pstmt) throws SQLException;
    }
}
