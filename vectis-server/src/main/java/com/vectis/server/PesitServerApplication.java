package com.vectis.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PeSIT Server Application
 * Implements PeSIT Hors-SIT profile over TCP/IP
 */
@SpringBootApplication
@EnableScheduling
public class PesitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PesitServerApplication.class, args);
    }
}
