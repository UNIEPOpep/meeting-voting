package com.heima.vote.data.model

import com.google.gson.annotations.SerializedName

// 发起投票请求
data class CreateVoteRequest(
    val topic: String,
    @SerializedName("file_number") val fileNumber: String,
    @SerializedName("vote_password") val votePassword: String,
    val deadline: String,
    @SerializedName("allow_abstain") val allowAbstain: Boolean,
    @SerializedName("allow_change_vote") val allowChangeVote: Boolean
)

// 创建投票响应
data class CreateVoteResponse(
    val id: Int,
    val message: String
)

// 加入投票请求
data class JoinVoteRequest(
    @SerializedName("file_number") val fileNumber: String,
    @SerializedName("vote_password") val votePassword: String
)

// 加入投票响应 / 投票详情
data class JoinVoteResponse(
    @SerializedName("session_id") val sessionId: Int,
    val topic: String,
    @SerializedName("file_number") val fileNumber: String,
    val deadline: String,
    @SerializedName("allow_abstain") val allowAbstain: Boolean,
    @SerializedName("allow_change_vote") val allowChangeVote: Boolean,
    @SerializedName("my_vote") val myVote: MyVoteInfo?
)

data class MyVoteInfo(
    val id: Int,
    val choice: String,
    @SerializedName("voted_at") val votedAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)

// 提交/修改投票请求
data class VoteChoiceRequest(
    val choice: String  // "agree" / "oppose" / "abstain"
)

// 投票列表项
data class VoteSessionItem(
    val id: Int,
    val topic: String,
    @SerializedName("file_number") val fileNumber: String,
    val deadline: String,
    @SerializedName("allow_abstain") val allowAbstain: Boolean,
    @SerializedName("allow_change_vote") val allowChangeVote: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("created_by_name") val createdByName: String? = null,
    @SerializedName("vote_count") val voteCount: Int? = null
)

// 投票汇总
data class VoteSummary(
    val total: Int,
    val agree: VoteStat,
    val oppose: VoteStat,
    val abstain: VoteStat
)

data class VoteStat(
    val count: Int,
    val percent: String
)

// 投票详情（汇总页）
data class VoteDetailResponse(
    val id: Int,
    val topic: String,
    @SerializedName("file_number") val fileNumber: String,
    val deadline: String,
    @SerializedName("allow_abstain") val allowAbstain: Boolean,
    @SerializedName("allow_change_vote") val allowChangeVote: Boolean,
    @SerializedName("created_by") val createdBy: String,
    @SerializedName("created_at") val createdAt: String,
    val summary: VoteSummary,
    val details: List<VoteDetailItem>
)

data class VoteDetailItem(
    val username: String,
    val choice: String,
    @SerializedName("voted_at") val votedAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)
