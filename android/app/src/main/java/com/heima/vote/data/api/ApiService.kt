package com.heima.vote.data.api

import com.heima.vote.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // === 认证 ===
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<Map<String, UserInfo>>

    // === 管理权限 ===
    @POST("api/admin/unlock")
    suspend fun unlock(@Body request: UnlockRequest): Response<MessageResponse>

    @PUT("api/admin/secret-key")
    suspend fun changeSecretKey(@Body request: ChangeKeyRequest): Response<MessageResponse>

    // === 用户管理（超管） ===
    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<MessageResponse>

    @GET("api/users")
    suspend fun getUsers(): Response<UserListResponse>

    // === 投票 ===
    @POST("api/voting-sessions")
    suspend fun createVote(@Body request: CreateVoteRequest): Response<CreateVoteResponse>

    @GET("api/voting-sessions")
    suspend fun getVoteSessions(): Response<Map<String, List<VoteSessionItem>>>

    @POST("api/voting-sessions/join")
    suspend fun joinVote(@Body request: JoinVoteRequest): Response<JoinVoteResponse>

    @GET("api/voting-sessions/{id}")
    suspend fun getVoteDetail(@Path("id") id: Int): Response<VoteDetailResponse>

    @POST("api/voting-sessions/{id}/vote")
    suspend fun submitVote(
        @Path("id") id: Int,
        @Body request: VoteChoiceRequest
    ): Response<MessageResponse>

    @PUT("api/voting-sessions/{id}/vote")
    suspend fun changeVote(
        @Path("id") id: Int,
        @Body request: VoteChoiceRequest
    ): Response<MessageResponse>
}
