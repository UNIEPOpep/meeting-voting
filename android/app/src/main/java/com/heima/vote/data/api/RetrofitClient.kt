package com.heima.vote.data.api

import com.google.gson.Gson
import com.heima.vote.BuildConfig
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

    // 开发/生产环境自动切换
    val BASE_URL: String
        get() = if (BuildConfig.DEBUG) {
            "http://10.0.2.2:3000/"   // 模拟器
        } else {
            "https://your-server.com/"  // TODO: 部署后改为实际HTTPS地址
        }

    private var tokenManager: TokenManager? = null
    private var apiService: ApiService? = null

    // 缓存令牌，避免每次请求都阻塞读取 DataStore
    @Volatile
    private var cachedToken: String? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
        // 初始加载令牌
        cachedToken = runBlocking { tm.tokenFlow.first() }
    }

    fun updateCachedToken(token: String?) {
        cachedToken = token
    }

    fun getApiService(): ApiService {
        if (apiService == null) {
            apiService = createApiService()
        }
        return apiService!!
    }

    private fun createApiService(): ApiService {
        // 日志拦截器：仅调试模式打印，正式版只打印请求头不含请求体
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // 认证拦截器：自动附加JWT令牌（使用缓存）
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val request = if (cachedToken != null) {
                original.newBuilder()
                    .header("Authorization", "Bearer $cachedToken")
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
