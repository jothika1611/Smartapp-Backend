package com.smartpay.shared.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Money leaves the API as a decimal string such as "500.50", never as a JSON number that a
 * JavaScript client could turn into a float. BigDecimal is only used for money in SmartPay.
 */
@Configuration
public class MoneyJsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer moneyAsDecimalString() {
        return builder -> builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeString(value.toPlainString());
            }
        });
    }
}
