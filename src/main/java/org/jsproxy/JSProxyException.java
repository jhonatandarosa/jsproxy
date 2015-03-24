package org.jsproxy;

public class JSProxyException extends RuntimeException {

    public JSProxyException() {
    }

    public JSProxyException(String message) {
        super(message);
    }

    public JSProxyException(String message, Throwable cause) {
        super(message, cause);
    }

    public JSProxyException(Throwable cause) {
        super(cause);
    }

    public JSProxyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
