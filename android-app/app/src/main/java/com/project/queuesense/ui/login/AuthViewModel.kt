package com.project.queuesense.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.queuesense.data.local.TokenStorageProvider
import com.project.queuesense.data.model.AuthUser
import com.project.queuesense.data.model.LoginRequest
import com.project.queuesense.data.model.RegisterRequest
import com.project.queuesense.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: AuthUser? = null,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val repository = QueueRepository()
    private val tokenStorage = TokenStorageProvider.getInstance()

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoading = !tokenStorage.getToken().isNullOrBlank()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedToken = tokenStorage.getToken()

        if (savedToken.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val user = repository.getCurrentUser()

                _uiState.value = AuthUiState(
                    isAuthenticated = true,
                    user = user
                )

            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    tokenStorage.clearToken()
                }

                _uiState.value = AuthUiState()

            } catch (e: Exception) {
                _uiState.value = AuthUiState(
                    errorMessage = "Could not verify your session. Please check your connection."
                )
            }
        }
    }

    fun login(mobile: String, password: String) {
        if (mobile.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter your mobile number and password."
            )
            return
        }

        authenticate {
            val response = repository.login(
                LoginRequest(
                    mobile = mobile.trim(),
                    password = password
                )
            )

            tokenStorage.saveToken(response.token)

            _uiState.value = AuthUiState(
                isAuthenticated = true,
                user = response.user
            )
        }
    }

    fun register(fullName: String, mobile: String, password: String) {
        if (fullName.isBlank() || mobile.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please complete all fields."
            )
            return
        }

        if (password.length < 8) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Password must be at least 8 characters."
            )
            return
        }

        authenticate {
            val response = repository.register(
                RegisterRequest(
                    fullName = fullName.trim(),
                    mobile = mobile.trim(),
                    password = password
                )
            )

            tokenStorage.saveToken(response.token)

            _uiState.value = AuthUiState(
                isAuthenticated = true,
                user = response.user
            )
        }
    }

    private fun authenticate(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                action()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = readableError(e)
                )
            }
        }
    }

    /**
     * Turns a failed request into a message a patient can understand.
     * Uses the server's own message when it sends one.
     */
    private fun readableError(e: Exception): String {
        if (e is HttpException) {
            parseServerDetail(e)?.let { return it }

            return when (e.code()) {
                401 -> "Invalid mobile number or password."
                409 -> "An account with this mobile number already exists."
                422 -> "Please check the details you entered."
                503 -> "The service is temporarily unavailable. Please try again."
                else -> "Authentication failed. Please try again."
            }
        }

        if (e is IOException) {
            return "Could not reach the server. Please check your connection."
        }

        return e.message ?: "Authentication failed. Please try again."
    }

    /**
     * Reads the "detail" field of the server's error response.
     * It is either a plain message or a list of field errors.
     */
    private fun parseServerDetail(e: HttpException): String? {
        return try {
            val body = e.response()?.errorBody()?.string().orEmpty()

            if (body.isBlank()) return null

            when (val detail = JSONObject(body).opt("detail")) {
                is String -> detail

                is JSONArray -> {
                    val first = detail.optJSONObject(0) ?: return null
                    val message = first.optString("msg")

                    if (message.isBlank()) return null

                    val location = first.optJSONArray("loc")
                    val field = location?.optString(location.length() - 1)

                    val fieldName = when (field) {
                        "full_name" -> "Full name"
                        "mobile" -> "Mobile number"
                        "password" -> "Password"
                        else -> null
                    }

                    if (fieldName != null) "$fieldName: $message" else message
                }

                else -> null
            }
        } catch (ex: Exception) {
            null
        }
    }

    fun logout() {
        tokenStorage.clearToken()
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}