package com.ktc.chungnam3.remembrall.geofence.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeofenceProperties.class)
public class GeofenceConfiguration {}
