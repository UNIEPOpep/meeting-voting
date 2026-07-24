package com.heima.vote.data.model

import com.google.gson.annotations.SerializedName

// 登录请求
data class LoginRequest(
    val username: String,
    val password: String
)

// 登录响应
data class LoginResponse(
    val token: String,
    val user: UserInfo
)

// 用户信息
data class UserInfo(
    val id: Int,
    val username: String,
    val role: String
)

// 通用响应
data class MessageResponse(
    val message: String,
    val token: String? = null,
    val unlocked: Boolean? = null
)

// 错误响应
data class ErrorResponse(
    val error: String
)
