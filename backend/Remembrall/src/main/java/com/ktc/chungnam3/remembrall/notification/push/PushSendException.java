package com.ktc.chungnam3.remembrall.notification.push;

public class PushSendException extends Exception {
    private final String failureCode;

    public PushSendException(String failureCode) {
        super(failureCode);
        this.failureCode = failureCode;
    }

    public PushSendException(String failureCode, Throwable cause) {
        super(failureCode, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
