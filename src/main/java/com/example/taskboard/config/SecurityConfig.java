package com.example.taskboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// 立ち上げ確認用。10日目に本実装（ログイン必須・Googleログイン）へ差し替える
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.headers(headers -> headers.frameOptions(frame -> frame.disable()));
		return http.build();
	}
}