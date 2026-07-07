package com.loadlab.sut;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulationProperties.class)
public class SutApplication {
	public static void main(String[] args) {
		SpringApplication.run(SutApplication.class, args);
	}
}