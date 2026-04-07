import quickfix.Message;
import quickfix.SessionID;

public class EnvCheck {
    public static void main(String[] args) {
        System.out.println("--- ENVIRONMENT DIAGNOSTIC ---");

        System.out.println("Java Version: " + System.getProperty("java.version"));

        try {
            Message msg = new Message();
            SessionID session = new SessionID("FIX.4.4", "SENDER", "TARGET");

            System.out.println("QuickFIX/J Library: DETECTED & FUNCTIONAL");
            System.out.println("Test Message Constructed: " + msg.toString());
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: QuickFIX/J libraries not found in Classpath.");
            e.printStackTrace();
        }
    }
}