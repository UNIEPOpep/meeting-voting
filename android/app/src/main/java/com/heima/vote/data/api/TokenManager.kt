package com.heima.vote.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore单例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_ROLE = stringPreferencesKey("role")
    }

    // 观察令牌变化
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }

    // 获取当前令牌
    suspend fun getToken(): String? {
        return context.dataStore.data.first()[KEY_TOKEN]
    }

    // 保存登录信息
    suspend fun saveAuth(token: String, username: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
            prefs[KEY_ROLE] = role
        }
        RetrofitClient.updateCachedToken(token)
    }

    // 更新令牌（解锁管理员后）
    suspend fun updateToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
        }
        RetrofitClient.updateCachedToken(token)
    }

    // 更新角色
    suspend fun updateRole(role: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ROLE] = role
        }
    }

    // 清除登录信息
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
