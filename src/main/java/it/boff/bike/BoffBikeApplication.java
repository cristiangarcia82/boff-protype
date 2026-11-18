package it.boff.bike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BoffBikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoffBikeApplication.class, args);
    }
}
