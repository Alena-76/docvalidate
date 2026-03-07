package com.bank.docvalidate.security

import com.bank.docvalidate.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * Spring Security calls loadUserByUsername() during authentication.
 * We load from the database and wrap in UserDetailsImpl.
 *
 * @Service: marks as a Spring service bean
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found: $username") }
        return UserDetailsImpl(user)
    }
}
