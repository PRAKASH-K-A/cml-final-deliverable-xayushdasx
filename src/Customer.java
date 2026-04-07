import java.math.BigDecimal;

/**
 * POJO representing a row in the customer_master table.
 */
public class Customer {

    private final String     customerCode;
    private final String     customerName;
    private final String     customerType;   // "INSTITUTIONAL" or "RETAIL"
    private final BigDecimal creditLimit;

    public Customer(String customerCode, String customerName, String customerType, BigDecimal creditLimit) {
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.customerType = customerType;
        this.creditLimit  = creditLimit;
    }

    public String     getCustomerCode() { return customerCode; }
    public String     getCustomerName() { return customerName; }
    public String     getCustomerType() { return customerType; }
    public BigDecimal getCreditLimit()  { return creditLimit;  }

    @Override
    public String toString() {
        return String.format("Customer{code=%s, type=%s, limit=%s}", customerCode, customerType, creditLimit);
    }
}
