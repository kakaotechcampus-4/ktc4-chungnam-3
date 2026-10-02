package com.ktc.chungnam3.remembrall.notification.push;

public class UnavailablePushSender implements PushSender {
    @Override
    public String send(PushMessage message) throws PushSendException {
        throw new PushSendException("FCM_NOT_CONFIGURED");
    }
}
