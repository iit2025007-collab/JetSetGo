package com.jetsetgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JetSetGoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JetSetGoApplication.class, args);
    }
}
