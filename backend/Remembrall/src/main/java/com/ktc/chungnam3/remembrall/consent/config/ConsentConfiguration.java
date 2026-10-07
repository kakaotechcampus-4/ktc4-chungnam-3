package com.ktc.chungnam3.remembrall.consent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsentProperties.class)
public class ConsentConfiguration {
}
