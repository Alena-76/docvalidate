package com.bank.docvalidate.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * Handles everything JWT-related.
 *
 * WHAT IS JWT?
 * A JSON Web Token is a self-contained token with 3 base64-encoded parts:
 *   Header.Payload.Signature
 *
 * The server signs the payload with a secret key. On every request, the server
 * verifies the signature — no need to hit the database for every auth check!
 *
 * @Component: registers this as a Spring bean (injectable anywhere via @Autowired / constructor injection)
 * @Value: injects values from application.properties
 */
@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.expiration-ms}") private val jwtExpirationMs: Long
) {
    private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    // Derive a strong key from the secret string
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    /** Generate a JWT after successful login */
    fun generateToken(authentication: Authentication): String {
        val userDetails = authentication.principal as UserDetailsImpl
        val now = Date()
        val expiry = Date(now.time + jwtExpirationMs)

        return Jwts.builder()
            .subject(userDetails.username)
            .issuedAt(now)
            .expiration(expiry)
            .claim("roles", userDetails.authorities.map { it.authority })
            .signWith(signingKey)
            .compact()
    }

    /** Extract username from a valid JWT */
    fun getUsernameFromToken(token: String): String =
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject

    /** Validate token — returns false instead of throwing, so we can return 401 cleanly */
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token)
            true
        } catch (ex: JwtException) {
            log.warn("Invalid JWT token: ${ex.message}")
            false
        } catch (ex: IllegalArgumentException) {
            log.warn("JWT claims string is empty: ${ex.message}")
            false
        }
    }
}
