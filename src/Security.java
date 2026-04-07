/**
 * POJO representing a row in the security_master table.
 * Preloaded into memory at startup for fast symbol validation.
 */
public class Security {

    private final String symbol;
    private final String securityType;  // e.g. "CS" (Common Stock)
    private final String description;
    private final int    lotSize;

    public Security(String symbol, String securityType, String description, int lotSize) {
        this.symbol       = symbol;
        this.securityType = securityType;
        this.description  = description;
        this.lotSize      = lotSize;
    }

    public String getSymbol()       { return symbol;       }
    public String getSecurityType() { return securityType; }
    public String getDescription()  { return description;  }
    public int    getLotSize()      { return lotSize;       }

    @Override
    public String toString() {
        return String.format("Security{symbol=%s, type=%s, lotSize=%d}", symbol, securityType, lotSize);
    }
}
