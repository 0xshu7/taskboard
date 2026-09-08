package com.example.taskboard.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.taskboard.repository.UserRepository;

@Configuration
@Profile("dev")
public class DevDataLoader {
	
	
	@Bean
	CommandLineRunner loadDevData(UserRepository users) {
		return args -> {
			if (users.findByEmail("dev@"))
		}
	}


}
