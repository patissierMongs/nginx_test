package com.nginx.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Was4Application {

    public static void main(String[] args) {
        SpringApplication.run(Was4Application.class, args);
    }
}
