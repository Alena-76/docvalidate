package com.bank.docvalidate.config

import com.bank.docvalidate.entity.User
import com.bank.docvalidate.enums.UserRole
import com.bank.docvalidate.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Seeds initial data when the app starts.
 *
 * @Profile("dev"): this bean only activates in the 'dev' profile.
 * In production, you'd use Flyway/Liquibase migrations instead.
 *
 * CommandLineRunner: Spring calls run() after the context is fully initialized.
 * Great for startup tasks like seeding data.
 */
@Configuration
@Profile("dev")
class DataInitializer {

    private val log = LoggerFactory.getLogger(DataInitializer::class.java)

    @Bean
    fun seedData(userRepository: UserRepository, passwordEncoder: PasswordEncoder): CommandLineRunner {
        return CommandLineRunner {
            if (userRepository.count() > 0) {
                log.info("Database already seeded, skipping.")
                return@CommandLineRunner
            }

            log.info("Seeding initial users...")

            // Create admin user
            userRepository.save(User(
                username = "admin",
                password = passwordEncoder.encode("Admin123!"),
                fullName = "System Administrator",
                email = "admin@bank.com",
                roles = setOf(UserRole.ROLE_ADMIN, UserRole.ROLE_SUPERVISOR, UserRole.ROLE_REVIEWER)
            ))

            // Create a supervisor
            userRepository.save(User(
                username = "supervisor",
                password = passwordEncoder.encode("Super123!"),
                fullName = "Jane Supervisor",
                email = "jane.supervisor@bank.com",
                roles = setOf(UserRole.ROLE_SUPERVISOR, UserRole.ROLE_REVIEWER)
            ))

            // Create two reviewers
            userRepository.save(User(
                username = "reviewer1",
                password = passwordEncoder.encode("Review123!"),
                fullName = "John Reviewer",
                email = "john.reviewer@bank.com",
                roles = setOf(UserRole.ROLE_REVIEWER)
            ))

            userRepository.save(User(
                username = "reviewer2",
                password = passwordEncoder.encode("Review123!"),
                fullName = "Sarah Reviewer",
                email = "sarah.reviewer@bank.com",
                roles = setOf(UserRole.ROLE_REVIEWER)
            ))

            log.info("✅ Seeded 4 users. Login credentials:")
            log.info("   admin / Admin123!        (ADMIN + SUPERVISOR + REVIEWER)")
            log.info("   supervisor / Super123!   (SUPERVISOR + REVIEWER)")
            log.info("   reviewer1 / Review123!   (REVIEWER)")
            log.info("   reviewer2 / Review123!   (REVIEWER)")
        }
    }
}
