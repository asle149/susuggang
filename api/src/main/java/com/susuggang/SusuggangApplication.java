package com.susuggang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SusuggangApplication {

	public static void main(String[] args) {
		SpringApplication.run(SusuggangApplication.class, args);
	}

}
