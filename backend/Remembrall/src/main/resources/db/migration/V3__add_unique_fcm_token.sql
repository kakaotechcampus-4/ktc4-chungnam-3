CREATE UNIQUE INDEX uk_device_fcm_token
ON device (fcm_token)
WHERE fcm_token IS NOT NULL;
