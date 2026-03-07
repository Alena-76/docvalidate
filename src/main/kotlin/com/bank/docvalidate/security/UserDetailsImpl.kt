package com.bank.docvalidate.security

import com.bank.docvalidate.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Spring Security needs a UserDetails object to understand the authenticated user.
 * This class wraps our User entity and adapts it to Spring Security's interface.
 *
 * KEY CONCEPT: Spring Security is interface-driven. By implementing UserDetails,
 * Spring Security can work with our domain model without knowing anything about it.
 */
class UserDetailsImpl(private val user: User) : UserDetails {

    val id: Long get() = user.id
    val fullName: String get() = user.fullName
    val email: String get() = user.email

    override fun getUsername(): String = user.username

    override fun getPassword(): String = user.password

    // Convert our UserRole enum set → Spring GrantedAuthority set
    override fun getAuthorities(): Collection<GrantedAuthority> =
        user.roles.map { SimpleGrantedAuthority(it.name) }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = user.isActive
}
