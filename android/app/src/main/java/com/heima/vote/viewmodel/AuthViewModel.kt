package com.heima.vote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heima.vote.data.api.RetrofitClient
import com.heima.vote.data.api.TokenManager
import com.heima.vote.data.model.LoginRequest
import com.heima.vote.data.model.UnlockRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val role: String = "user",           // "user" / "admin" / "super_admin"
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val token: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val api = RetrofitClient.getApiService()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        RetrofitClient.init(tokenManager)
        // 检查是否有保存的登录状态
        viewModelScope.launch {
            tokenManager.tokenFlow.collect { token ->
                if (token != null) {
                    _uiState.value = _uiState.value.copy(isLoggedIn = true, token = token)
                }
            }
        }
    }

    // 登录
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.login(LoginRequest(username, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.saveAuth(body.token, body.user.username, body.user.role)
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        username = body.user.username,
                        role = body.user.role,
                        token = body.token,
                        isLoading = false
                    )
                } else {
                    val error = RetrofitClient.parseError(response.errorBody())
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络连接失败: ${e.message}"
                )
            }
        }
    }

    // 密钥解锁管理员
    fun unlockAdmin(secretKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.unlock(UnlockRequest(secretKey))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.token != null) {
                        tokenManager.updateToken(body.token)
                        tokenManager.updateRole("admin")
                        _uiState.value = _uiState.value.copy(
                            role = "admin",
                            message = body.message,
                            token = body.token,
                            isLoading = false
                        )
                    } else if (body.unlocked == true) {
                        // 已经是管理员
                        _uiState.value = _uiState.value.copy(
                            message = body.message,
                            isLoading = false
                        )
                    }
                } else {
                    val error = RetrofitClient.parseError(response.errorBody())
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络连接失败: ${e.message}"
                )
            }
        }
    }

    // 退出登录
    fun logout() {
        viewModelScope.launch {
            tokenManager.clear()
            _uiState.value = AuthUiState()
        }
    }

    // 清除消息
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
