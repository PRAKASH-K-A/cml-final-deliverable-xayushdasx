CREATE DATABASE trading_system;
USE trading_system;

CREATE TABLE customer_master (
    customer_code VARCHAR(20) PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    customer_type VARCHAR(20) NOT NULL,
    credit_limit DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE security_master (
    symbol VARCHAR(16) PRIMARY KEY,
    security_type VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    lot_size INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    order_id VARCHAR(64) PRIMARY KEY,
    cl_ord_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    side CHAR(1) NOT NULL,
    price DOUBLE NOT NULL,
    quantity DOUBLE NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_symbol
        FOREIGN KEY (symbol) REFERENCES security_master(symbol)
);

CREATE INDEX idx_orders_cl_ord_id ON orders(cl_ord_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE executions (
    exec_id VARCHAR(64) PRIMARY KEY,
    incoming_order_id VARCHAR(64) NOT NULL,
    resting_order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    aggressor_side CHAR(1) NOT NULL,
    price DOUBLE NOT NULL,
    quantity DOUBLE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exec_incoming_order
        FOREIGN KEY (incoming_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_exec_resting_order
        FOREIGN KEY (resting_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_exec_symbol
        FOREIGN KEY (symbol) REFERENCES security_master(symbol)
);

CREATE INDEX idx_exec_symbol ON executions(symbol);
CREATE INDEX idx_exec_incoming_order ON executions(incoming_order_id);
CREATE INDEX idx_exec_resting_order ON executions(resting_order_id);

INSERT INTO customer_master (customer_code, customer_name, customer_type, credit_limit) VALUES
('CUST001', 'Alpha Capital', 'INSTITUTIONAL', 10000000.00),
('CUST002', 'Beta Investments', 'INSTITUTIONAL', 7500000.00),
('CUST003', 'Retail Demo Client', 'RETAIL', 100000.00);

INSERT INTO security_master (symbol, security_type, description, lot_size) VALUES
('GOOG', 'CS', 'Alphabet Inc. Class C', 1),
('MSFT', 'CS', 'Microsoft Corporation', 1),
('IBM', 'CS', 'International Business Machines', 1),
('INFY', 'CS', 'Infosys Limited', 1);