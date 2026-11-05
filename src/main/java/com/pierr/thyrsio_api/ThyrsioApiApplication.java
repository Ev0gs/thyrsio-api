package com.pierr.thyrsio_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ThyrsioApiApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(ThyrsioApiApplication.class, args);
	}

}