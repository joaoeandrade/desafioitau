package com.example.eventplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EventPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventPlatformApplication.class, args);
    }
}
