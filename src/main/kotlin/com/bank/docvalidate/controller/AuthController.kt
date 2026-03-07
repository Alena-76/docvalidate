package com.bank.docvalidate.controller

import com.bank.docvalidate.dto.ApiResponse
import com.bank.docvalidate.dto.LoginRequest
import com.bank.docvalidate.dto.LoginResponse
import com.bank.docvalidate.dto.RegisterRequest
import com.bank.docvalidate.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Handles authentication (login / register).
 *
 * KEY CONCEPTS:
 *  @RestController = @Controller + @ResponseBody (auto-serializes return values to JSON)
 *  @RequestMapping: base path for all endpoints in this controller
 *  @PostMapping: maps POST requests to this method
 *  @Valid: triggers bean validation (the @NotBlank etc. on DTO fields)
 *  @RequestBody: deserializes JSON request body into the DTO
 *  @ResponseStatus: sets the HTTP status code on the response
 *
 * Swagger annotations (@Operation, @Tag) appear in the auto-generated API docs at /swagger-ui.html
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login and registration")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    @Operation(summary = "Login with username and password, receive JWT token")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        val response = authService.login(request)
        return ApiResponse(data = response, message = "Login successful")
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new reviewer account")
    fun register(@Valid @RequestBody request: RegisterRequest): ApiResponse<String> {
        val user = authService.register(request)
        return ApiResponse(data = user.username, message = "User registered successfully. Default role: REVIEWER")
    }
}
