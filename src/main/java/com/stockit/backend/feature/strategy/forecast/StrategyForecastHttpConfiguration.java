package com.stockit.backend.feature.strategy.forecast;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ML 수요예측 전용 HTTP Client의 연결 제한과 JSON 직렬화 정책 구성
 */
@Configuration
@EnableConfigurationProperties(StrategyForecastProperties.class)
public class StrategyForecastHttpConfiguration {

    /**
     * 애플리케이션 공용 Jackson 날짜 정책을 적용한 수요예측 전용 Builder 생성
     */
    @Bean
    public RestClient.Builder strategyForecastRestClientBuilder(
            StrategyForecastProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    // LocalDate가 ML 계약의 ISO 문자열로 직렬화되도록 기본 Converter 교체
                    converters.removeIf(
                            MappingJackson2HttpMessageConverter.class::isInstance
                    );
                    converters.add(
                            new MappingJackson2HttpMessageConverter(objectMapper)
                    );
                });
    }
}
