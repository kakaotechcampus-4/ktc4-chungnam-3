package com.ktc.chungnam3.remembrall.notification.push;

public interface PushSender {
    String send(PushMessage message) throws PushSendException;
}
