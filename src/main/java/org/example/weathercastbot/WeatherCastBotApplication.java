package org.example.weathercastbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WeatherCastBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherCastBotApplication.class, args);
    }

}
