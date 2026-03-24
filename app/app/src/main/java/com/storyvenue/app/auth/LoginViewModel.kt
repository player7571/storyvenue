package com.storyvenue.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
)

class LoginViewModel(
    private val authRepository: AuthRepository = PlaceholderAuthRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onEmailChanged(email: String) {
        uiState = uiState.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        uiState = uiState.copy(password = password, errorMessage = null)
    }

    fun onLoginClick() {
        if (uiState.isLoading) return

        val email = uiState.email.trim()
        val password = uiState.password

        if (email.isBlank() || password.isBlank()) {
            uiState = uiState.copy(
                errorMessage = "이메일과 비밀번호를 모두 입력해 주세요.",
                isLoginSuccessful = false,
            )
            return
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null, isLoginSuccessful = false)

        viewModelScope.launch {
            val result = authRepository.signIn(email = email, password = password)

            uiState = result.fold(
                onSuccess = {
                    uiState.copy(
                        email = email,
                        isLoading = false,
                        errorMessage = null,
                        isLoginSuccessful = true,
                    )
                },
                onFailure = { error ->
                    uiState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "로그인에 실패했습니다.",
                        isLoginSuccessful = false,
                    )
                },
            )
        }
    }

    fun onLoginNavigationComplete() {
        uiState = uiState.copy(isLoginSuccessful = false)
    }
}
