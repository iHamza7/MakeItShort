package com.example.makeitshort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MakeitshortApplication {

    public static void main(String[] args) {
        SpringApplication.run(MakeitshortApplication.class, args);
    }

}
