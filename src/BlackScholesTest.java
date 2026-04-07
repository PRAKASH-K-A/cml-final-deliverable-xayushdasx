public class BlackScholesTest {
    public static void main(String[] args) {
        double call = BlackScholes.callPrice(100.0, 100.0, 1.0, 0.05, 0.2);
        double put = BlackScholes.putPrice(100.0, 100.0, 1.0, 0.05, 0.2);

        System.out.printf("Call price: %.4f%n", call);
        System.out.printf("Put price: %.4f%n", put);

        double expectedCall = 10.45;
        if (Math.abs(call - expectedCall) > 0.25) {
            throw new IllegalStateException("Call price deviates from expected value.");
        }

        System.out.println("Black-Scholes unit test passed.");
    }
}
