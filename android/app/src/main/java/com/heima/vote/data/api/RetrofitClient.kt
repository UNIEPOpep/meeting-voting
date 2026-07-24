package com.heima.vote.data.api

import com.google.gson.Gson
import com.heima.vote.data.model.ErrorResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // TODO: 部署到服务器后，替换为实际服务器地址
    // 开发时用 10.0.2.2 代表电脑本机（安卓模拟器中）
    // 真机测试时用电脑的局域网IP（如 192.168.x.x）
    const val BASE_URL = "http://10.0.2.2:3000/"

    private var tokenManager: TokenManager? = null
    private var apiService: ApiService? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
    }

    fun getApiService(): ApiService {
        if (apiService == null) {
            apiService = createApiService()
        }
        return apiService!!
    }

    private fun createApiService(): ApiService {
        val tm = tokenManager

        // 日志拦截器（调试用）
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 认证拦截器：自动附加JWT令牌
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = if (tm != null) {
                runBlocking { tm.tokenFlow.first() }
            } else null

            val request = if (token != null) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // 解析错误信息
    fun parseError(errorBody: okhttp3.ResponseBody?): String {
        return try {
            val gson = Gson()
            val error = gson.fromJson(errorBody?.charStream(), ErrorResponse::class.java)
            error.error
        } catch (e: Exception) {
            "网络请求失败，请检查网络连接"
        }
    }
}
