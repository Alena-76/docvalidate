package com.bank.docvalidate.service

import com.bank.docvalidate.dto.LoginRequest
import com.bank.docvalidate.dto.LoginResponse
import com.bank.docvalidate.dto.RegisterRequest
import com.bank.docvalidate.entity.User
import com.bank.docvalidate.enums.UserRole
import com.bank.docvalidate.exception.BadRequestException
import com.bank.docvalidate.repository.UserRepository
import com.bank.docvalidate.security.JwtTokenProvider
import com.bank.docvalidate.security.UserDetailsImpl
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Authentication service.
 *
 * @Service: marks as business logic layer; separates concerns from @Controller
 * @Transactional: wraps methods in a DB transaction automatically.
 *   If an exception is thrown, Spring rolls back all DB changes in that transaction.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun register(request: RegisterRequest): User {
        if (userRepository.existsByUsername(request.username))
            throw BadRequestException("Username '${request.username}' is already taken")
        if (userRepository.existsByEmail(request.email))
            throw BadRequestException("Email '${request.email}' is already registered")

        val user = User(
            username = request.username,
            password = passwordEncoder.encode(request.password),  // ALWAYS hash passwords!
            fullName = request.fullName,
            email = request.email,
            roles = setOf(UserRole.ROLE_REVIEWER)
        )
        return userRepository.save(user)
    }

    fun login(request: LoginRequest): LoginResponse {
        // AuthenticationManager verifies username + password via UserDetailsService
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password)
        )
        SecurityContextHolder.getContext().authentication = authentication

        val token = jwtTokenProvider.generateToken(authentication)
        val userDetails = authentication.principal as UserDetailsImpl

        return LoginResponse(
            token = token,
            username = userDetails.username,
            fullName = userDetails.fullName,
            roles = userDetails.authorities.map { it.authority }.toSet()
        )
    }
}
