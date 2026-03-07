package com.bank.docvalidate.config

import com.bank.docvalidate.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Central Spring Security configuration.
 *
 * KEY CONCEPTS:
 *  @Configuration: marks as a config class; Spring processes @Bean methods here
 *  @EnableWebSecurity: activates Spring Security's web support
 *  @EnableMethodSecurity: enables @PreAuthorize on controller/service methods
 *
 *  SecurityFilterChain: the new (Spring Boot 3+) way to configure HTTP security.
 *  Replaces the old WebSecurityConfigurerAdapter.
 *
 *  SessionCreationPolicy.STATELESS: REST APIs don't use sessions.
 *  The JWT carries all auth info, so we're fully stateless.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val userDetailsService: UserDetailsService,
    private val jwtFilter: JwtAuthenticationFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider =
        DaoAuthenticationProvider().apply {
            setUserDetailsService(userDetailsService)
            setPasswordEncoder(passwordEncoder())
        }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }  // Safe to disable for stateless JWT APIs
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints — no token required
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()  // Dev only

                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }
            // Allow H2 console iframes in dev
            .headers { it.frameOptions { frame -> frame.sameOrigin() } }

            // Register our JWT filter BEFORE Spring's built-in username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
