package com.bank.docvalidate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point for the DocValidate Spring Boot application.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   @Configuration     - marks this as a source of bean definitions
 *   @EnableAutoConfiguration - Spring Boot auto-configures beans based on classpath
 *   @ComponentScan    - scans this package and sub-packages for @Component, @Service, etc.
 *
 * Run this to start the app. It will:
 *  1. Start an embedded Tomcat server on port 8080
 *  2. Connect to H2 in-memory database (dev profile)
 *  3. Seed test users via DataInitializer
 *  4. Expose API at http://localhost:8080
 *  5. Swagger UI at http://localhost:8080/swagger-ui.html
 *  6. H2 Console at http://localhost:8080/h2-console
 */
@SpringBootApplication
class DocValidateApplication

fun main(args: Array<String>) {
    runApplication<DocValidateApplication>(*args)
}
