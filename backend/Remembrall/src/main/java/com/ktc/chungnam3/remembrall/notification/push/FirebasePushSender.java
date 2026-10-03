package com.ktc.chungnam3.remembrall.notification.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

public class FirebasePushSender implements PushSender, AutoCloseable {
    private final FirebaseApp app;

    public FirebasePushSender(FirebaseApp app) {
        this.app = app;
    }

    @Override
    public String send(PushMessage message) throws PushSendException {
        try {
            return FirebaseMessaging.getInstance(app).send(toFirebaseMessage(message));
        } catch (FirebaseMessagingException exception) {
            String code = exception.getMessagingErrorCode() == null
                    ? "FCM_SEND_ERROR" : exception.getMessagingErrorCode().name();
            throw new PushSendException(code, exception);
        }
    }

    @SuppressWarnings("deprecation")
    static Message toFirebaseMessage(PushMessage message) {
        return Message.builder().setToken(message.token())
                .setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder().setTtl(message.androidTtl().toMillis())
                        .setNotification(AndroidNotification.builder().setTag(message.androidTag()).build()).build())
                .setApnsConfig(ApnsConfig.builder().putAllHeaders(message.apnsHeaders())
                        .setAps(Aps.builder().build()).build())
                .build();
    }

    @Override
    public void close() {
        app.delete();
    }
}
