
package com.project.queuesense.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("full_name")
    val fullName: String,

    val mobile: String,
    val password: String
)

data class LoginRequest(
    val mobile: String,
    val password: String
)

data class AuthUser(
    val id: Long,

    @SerializedName("full_name")
    val fullName: String,

    val mobile: String
)

data class AuthResponse(
    val token: String,
    val user: AuthUser
)
