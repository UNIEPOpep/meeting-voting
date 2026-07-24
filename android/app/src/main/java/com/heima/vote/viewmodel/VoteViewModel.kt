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

data class VoteUiState(
    // 投票列表
    val sessions: List<VoteSessionItem> = emptyList(),
    // 当前投票详情
    val currentVote: JoinVoteResponse? = null,
    // 投票汇总
    val voteDetail: VoteDetailResponse? = null,
    // 创建投票表单
    val createTopic: String = "",
    val createFileNumber: String = "",
    val createVotePassword: String = "",
    val createDeadline: String = "",
    val createAllowAbstain: Boolean = true,
    val createAllowChangeVote: Boolean = false,
    // 加入投票表单
    val joinFileNumber: String = "",
    val joinVotePassword: String = "",
    // 状态
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class VoteViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.getApiService()

    private val _uiState = MutableStateFlow(VoteUiState())
    val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()

    // 更新表单字段
    fun updateCreateField(key: String, value: Any) {
        _uiState.value = when (key) {
            "topic" -> _uiState.value.copy(createTopic = value as String)
            "fileNumber" -> _uiState.value.copy(createFileNumber = value as String)
            "votePassword" -> _uiState.value.copy(createVotePassword = value as String)
            "deadline" -> _uiState.value.copy(createDeadline = value as String)
            "allowAbstain" -> _uiState.value.copy(createAllowAbstain = value as Boolean)
            "allowChangeVote" -> _uiState.value.copy(createAllowChangeVote = value as Boolean)
            else -> _uiState.value
        }
    }

    fun updateJoinField(key: String, value: String) {
        _uiState.value = when (key) {
            "fileNumber" -> _uiState.value.copy(joinFileNumber = value)
            "votePassword" -> _uiState.value.copy(joinVotePassword = value)
            else -> _uiState.value
        }
    }

    // 发起投票
    fun createVote() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = api.createVote(CreateVoteRequest(
                    topic = s.createTopic,
                    fileNumber = s.createFileNumber,
                    votePassword = s.createVotePassword,
                    deadline = s.createDeadline,
                    allowAbstain = s.createAllowAbstain,
                    allowChangeVote = s.createAllowChangeVote
                ))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "投票创建成功！文件编号: ${s.createFileNumber}",
                        createTopic = "", createFileNumber = "", createVotePassword = "",
                        createDeadline = ""
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

    // 加入投票
    fun joinVote() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.value = s.copy(isLoading = true, error = null)
            try {
                val response = api.joinVote(JoinVoteRequest(s.joinFileNumber, s.joinVotePassword))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentVote = response.body()
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

    // 提交投票
    fun submitVote(sessionId: Int, choice: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.submitVote(sessionId, VoteChoiceRequest(choice))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "投票成功！",
                        currentVote = null,
                        joinFileNumber = "",
                        joinVotePassword = ""
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

    // 修改投票
    fun changeVote(sessionId: Int, choice: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.changeVote(sessionId, VoteChoiceRequest(choice))
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "投票修改成功！",
                        currentVote = null
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

    // 加载投票列表
    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = api.getVoteSessions()
                if (response.isSuccessful) {
                    val sessions = response.body()?.get("sessions") ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessions = sessions
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // 加载投票汇总详情
    fun loadVoteDetail(sessionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.getVoteDetail(sessionId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        voteDetail = response.body()
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

    fun clearCurrentVote() {
        _uiState.value = _uiState.value.copy(currentVote = null)
    }

    fun clearVoteDetail() {
        _uiState.value = _uiState.value.copy(voteDetail = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
