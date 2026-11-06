package com.pierr.thyrsio_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@ConfigurationPropertiesScan
@SpringBootApplication
public class ThyrsioApiApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(ThyrsioApiApplication.class, args);
	}

}