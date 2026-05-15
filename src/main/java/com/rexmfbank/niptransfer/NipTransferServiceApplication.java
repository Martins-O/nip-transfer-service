package com.rexmfbank.niptransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NipTransferServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NipTransferServiceApplication.class, args);
    }
}
