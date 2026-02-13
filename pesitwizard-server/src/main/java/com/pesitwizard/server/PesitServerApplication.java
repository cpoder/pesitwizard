package com.pesitwizard.server;

import com.pesitwizard.security.SecretsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** PeSIT Server Application Implements PeSIT Hors-SIT profile over TCP/IP */
@SpringBootApplication
@Import(SecretsConfig.class)
@EnableScheduling
public class PesitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PesitServerApplication.class, args);
    }
}
