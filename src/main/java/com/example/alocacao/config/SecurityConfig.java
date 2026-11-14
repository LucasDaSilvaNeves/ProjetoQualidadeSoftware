package com.example.alocacao.config;

import com.example.alocacao.security.JsonAccessDeniedHandler;
import com.example.alocacao.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autorização por perfil, avaliada ANTES dos controllers (falha fechada: o que não
 * está listado é negado). CSRF desligado porque a API é stateless com HTTP Basic e
 * não usa cookies de sessão.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JsonAuthenticationEntryPoint entryPoint,
                                           JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // interface estática, documentação e health check
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/favicon.ico",
                                "/error", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()

                        .requestMatchers("/api/users", "/api/users/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/resources", "/api/resources/**").authenticated()
                        .requestMatchers("/api/resources", "/api/resources/**").hasRole("ADMINISTRADOR")

                        .requestMatchers(HttpMethod.POST, "/api/reservations")
                        .hasAnyRole("SOLICITANTE", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/reservations/*/approve",
                                "/api/reservations/*/reject",
                                "/api/reservations/*/checkout",
                                "/api/reservations/*/return",
                                "/api/reservations/*/no-show").hasRole("RESPONSAVEL")
                        .requestMatchers("/api/reservations", "/api/reservations/**").authenticated()

                        .requestMatchers("/api/audit", "/api/audit/**").hasAnyRole("RESPONSAVEL", "ADMINISTRADOR")
                        .requestMatchers("/api/reports", "/api/reports/**").hasAnyRole("RESPONSAVEL", "ADMINISTRADOR")
                        .requestMatchers("/api/notifications", "/api/notifications/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/me").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }
}
