package com.example.travelhelper_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TravelHelperServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelHelperServerApplication.class, args);
    }

}
