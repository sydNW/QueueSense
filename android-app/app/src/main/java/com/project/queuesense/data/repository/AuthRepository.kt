
package com.project.queuesense.data.repository

import com.project.queuesense.data.local.TokenStorage
import com.project.queuesense.data.model.AuthResponse
import com.project.queuesense.data.model.LoginRequest
import com.project.queuesense.data.model.RegisterRequest

class AuthRepository(
    private val queueRepository: QueueRepository,
    private val tokenStorage: TokenStorage
) {

    suspend fun login(
        mobile: String,
        password: String
    ): AuthResponse {
        val response = queueRepository.login(
            LoginRequest(mobile, password)
        )

        tokenStorage.saveToken(response.token)
        return response
    }

    suspend fun register(
        fullName: String,
        mobile: String,
        password: String
    ): AuthResponse {
        val response = queueRepository.register(
            RegisterRequest(fullName, mobile, password)
        )

        tokenStorage.saveToken(response.token)
        return response
    }

    fun logout() {
        tokenStorage.clearToken()
    }

    fun getSavedToken(): String? {
        return tokenStorage.getToken()
    }
}
