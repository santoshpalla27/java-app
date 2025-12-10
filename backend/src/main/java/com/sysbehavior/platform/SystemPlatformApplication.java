package com.sysbehavior.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SystemPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(SystemPlatformApplication.class, args);
	}

}
