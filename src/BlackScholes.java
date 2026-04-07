public final class BlackScholes {

    private BlackScholes() {}

    // Cumulative normal distribution approximation.
    public static double N(double z) {
        if (z > 6.0) {
            return 1.0;
        }
        if (z < -6.0) {
            return 0.0;
        }

        double b1 = 0.31938153;
        double b2 = -0.356563782;
        double b3 = 1.781477937;
        double b4 = -1.821255978;
        double b5 = 1.330274429;
        double p = 0.2316419;
        double c2 = 0.3989423;
        double a = Math.abs(z);
        double t = 1.0 / (1.0 + a * p);
        double b = c2 * Math.exp((-z) * z / 2.0);
        double n = ((((b5 * t + b4) * t + b3) * t + b2) * t + b1) * t;
        n = 1.0 - b * n;

        if (z < 0.0) {
            n = 1.0 - n;
        }
        return n;
    }

    public static double callPrice(double S, double K, double T, double r, double sigma) {
        validateInputs(S, K, T, sigma);
        double d1 = (Math.log(S / K) + (r + sigma * sigma / 2.0) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        return S * N(d1) - K * Math.exp(-r * T) * N(d2);
    }

    public static double putPrice(double S, double K, double T, double r, double sigma) {
        validateInputs(S, K, T, sigma);
        double d1 = (Math.log(S / K) + (r + sigma * sigma / 2.0) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        return K * Math.exp(-r * T) * N(-d2) - S * N(-d1);
    }

    private static void validateInputs(double S, double K, double T, double sigma) {
        if (S <= 0.0 || K <= 0.0 || T <= 0.0 || sigma <= 0.0) {
            throw new IllegalArgumentException("Black-Scholes inputs must be strictly positive.");
        }
    }
}
