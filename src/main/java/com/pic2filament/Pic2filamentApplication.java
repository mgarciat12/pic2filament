package com.pic2filament;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class Pic2filamentApplication {

	public static void main(String[] args) {
		SpringApplication.run(Pic2filamentApplication.class, args);
	}

}
