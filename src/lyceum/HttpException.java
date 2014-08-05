package lyceum;

public class HttpException extends Exception {
    private final int status;
    private final String message;

    public HttpException(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return this.status;
    }

    public String getMessage() {
        return this.message;
    }
}
