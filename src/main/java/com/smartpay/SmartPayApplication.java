package com.smartpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the SmartPay modular monolith.
 *
 * <p>This application is a portfolio simulation. It never connects to a real bank,
 * payment gateway, or card network and never moves real money.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPayApplication.class, args);
    }
}
