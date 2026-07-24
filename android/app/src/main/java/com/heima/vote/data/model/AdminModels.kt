package com.heima.vote.data.model

import com.google.gson.annotations.SerializedName

// 密钥解锁请求
data class UnlockRequest(
    @SerializedName("secret_key") val secretKey: String
)

// 修改密钥请求
data class ChangeKeyRequest(
    @SerializedName("old_key") val oldKey: String,
    @SerializedName("new_key") val newKey: String
)

// 创建用户请求
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String = "user"  // "user" 或 "admin"
)

// 用户列表项
data class UserListItem(
    val id: Int,
    val username: String,
    val role: String,
    @SerializedName("created_at") val createdAt: String
)

// 用户列表响应
data class UserListResponse(
    val users: List<UserListItem>
)
