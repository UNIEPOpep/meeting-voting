package com.heima.vote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heima.vote.data.api.RetrofitClient
import com.heima.vote.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    // 用户列表
    val users: List<UserListItem> = emptyList(),
    // 创建用户表单
    val newUsername: String = "",
    val newPassword: String = "",
    val newRole: String = "user",       // "user" / "admin"
    // 修改密钥
    val oldKey: String = "",
    val newKey: String = "",
    // 状态
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.getApiService()

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun updateField(key: String, value: String) {
        _uiState.value = when (key) {
            "newUsername" -> _uiState.value.copy(newUsername = value)
            "newPassword" -> _uiState.value.copy(newPassword = value)
            "newRole" -> _uiState.value.copy(newRole = value)
            "oldKey" -> _uiState.value.copy(oldKey = value)
            "newKey" -> _uiState.value.copy(newKey = value)
            else -> _uiState.value
        }
    }

    // 创建用户
    fun createUser() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = api.createUser(CreateUserRequest(
                    username = s.newUsername,
                    password = s.newPassword,
                    role = s.newRole
                ))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "用户创建成功",
                        newUsername = "",
                        newPassword = ""
                    )
                    loadUsers()
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

    // 加载用户列表
    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = api.getUsers()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        users = response.body()?.users ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // 修改密钥
    fun changeSecretKey() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = api.changeSecretKey(ChangeKeyRequest(s.oldKey, s.newKey))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "密钥修改成功",
                        oldKey = "",
                        newKey = ""
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

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
