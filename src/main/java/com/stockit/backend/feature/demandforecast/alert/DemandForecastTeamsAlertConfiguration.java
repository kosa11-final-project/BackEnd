package com.stockit.backend.feature.demandforecast.alert;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(DemandForecastTeamsAlertProperties.class)
public class DemandForecastTeamsAlertConfiguration {

    @Bean
    public RestClient demandForecastTeamsAlertRestClient(
            DemandForecastTeamsAlertProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.resolvedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.resolvedReadTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }
}
