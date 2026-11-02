package com.ktc.chungnam3.remembrall.save.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4는 Jackson 3({@code tools.jackson.databind.ObjectMapper})용 {@code ObjectMapper}만
 * 자동 등록하고, Jackson 2({@code com.fasterxml.jackson.databind.ObjectMapper})는 등록해주지 않는다.
 * {@code SavePipelineService}/{@code ConfirmAnswerService}/{@code TourApiClient}가 전부 Jackson 2
 * {@code ObjectMapper}를 생성자로 주입받고 있어, 이 Bean이 없으면 그 세 곳에서
 * {@code NoSuchBeanDefinitionException}으로 애플리케이션 컨텍스트 기동 자체가 실패한다
 * (PR 리뷰, doheelab-coder).
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2Config {

    @Bean
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper();
    }
}
