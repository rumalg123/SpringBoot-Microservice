package com.rumal.personalization_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PersonalizationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PersonalizationServiceApplication.class, args);
	}

}
