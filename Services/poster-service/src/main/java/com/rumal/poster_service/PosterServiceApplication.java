package com.rumal.poster_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PosterServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PosterServiceApplication.class, args);
	}

}
