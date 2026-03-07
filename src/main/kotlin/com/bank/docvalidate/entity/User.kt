package com.bank.docvalidate.entity

import com.bank.docvalidate.enums.UserRole
import jakarta.persistence.*
import java.time.Instant

/**
 * Represents a bank staff member who can review documents.
 *
 * Spring Boot concepts illustrated here:
 *  - @Entity: marks this class as a JPA-managed database table
 *  - @Table: customizes table name / constraints
 *  - @GeneratedValue: auto-increment primary key strategy
 *  - @ElementCollection: stores a collection of simple values in a separate table
 *  - @Enumerated: stores enum as string (readable) not ordinal (fragile)
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["username"])]
)
data class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 50)
    val username: String,

    @Column(nullable = false)
    val password: String,   // BCrypt hashed — never store plain text!

    @Column(nullable = false, length = 100)
    val fullName: String,

    @Column(nullable = false, unique = true, length = 100)
    val email: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    val roles: Set<UserRole> = setOf(UserRole.ROLE_REVIEWER),

    val isActive: Boolean = true,

    val createdAt: Instant = Instant.now()
)
