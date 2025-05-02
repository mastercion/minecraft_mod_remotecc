package xyz.d1mx;

public class ConnectResult {
    private final boolean success;
    private final String message;

    public ConnectResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}