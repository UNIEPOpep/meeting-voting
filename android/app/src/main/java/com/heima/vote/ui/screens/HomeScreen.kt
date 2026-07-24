package com.heima.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heima.vote.ui.theme.*
import com.heima.vote.viewmodel.AuthViewModel
import com.heima.vote.viewmodel.VoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    voteViewModel: VoteViewModel,
    onNavigateToAdmin: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val voteState by voteViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("黑马投票") },
                actions = {
                    // 管理员入口
                    if (authState.role == "admin" || authState.role == "super_admin") {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = "管理")
                        }
                    }
                    IconButton(onClick = { authViewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 用户信息卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("欢迎，${authState.username}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (authState.role) {
                                "super_admin" -> "超级管理员"
                                "admin" -> "管理员"
                                else -> "普通用户"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SubTextColor
                        )
                    }
                }
            }

            // 提示信息
            if (voteState.message != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AgreeColor.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AgreeColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(voteState.message!!)
                    }
                }
                // Auto clear
                LaunchedEffect(voteState.message) {
                    kotlinx.coroutines.delay(3000)
                    voteViewModel.clearMessage()
                }
            }

            if (voteState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OpposeColor.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = OpposeColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(voteState.error!!, color = OpposeColor)
                    }
                }
                LaunchedEffect(voteState.error) {
                    kotlinx.coroutines.delay(3000)
                    voteViewModel.clearMessage()
                }
            }

            // ===== 参与投票区 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("参与投票", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = voteState.joinFileNumber,
                        onValueChange = { voteViewModel.updateJoinField("fileNumber", it) },
                        label = { Text("文件编号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = voteState.joinVotePassword,
                        onValueChange = { voteViewModel.updateJoinField("votePassword", it) },
                        label = { Text("投票密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { voteViewModel.joinVote() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !voteState.isLoading &&
                                voteState.joinFileNumber.isNotBlank() &&
                                voteState.joinVotePassword.isNotBlank()
                    ) {
                        if (voteState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Text("进入投票")
                    }
                }
            }

            // ===== 投票区（加入后显示） =====
            voteState.currentVote?.let { vote ->
                VotingCard(
                    sessionId = vote.sessionId,
                    topic = vote.topic,
                    allowAbstain = vote.allowAbstain,
                    allowChangeVote = vote.allowChangeVote,
                    myVote = vote.myVote,
                    onSubmit = { choice -> voteViewModel.submitVote(vote.sessionId, choice) },
                    onChange = { choice -> voteViewModel.changeVote(vote.sessionId, choice) },
                    onCancel = { voteViewModel.clearCurrentVote() },
                    isLoading = voteState.isLoading
                )
            }

            // ===== 发起投票区（管理员可见） =====
            if (authState.role == "admin" || authState.role == "super_admin") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("发起投票", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = voteState.createTopic,
                            onValueChange = { voteViewModel.updateCreateField("topic", it) },
                            label = { Text("投票主题") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = voteState.createFileNumber,
                                onValueChange = { voteViewModel.updateCreateField("fileNumber", it) },
                                label = { Text("文件编号") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = voteState.createVotePassword,
                                onValueChange = { voteViewModel.updateCreateField("votePassword", it) },
                                label = { Text("投票密码") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = voteState.createDeadline,
                            onValueChange = { voteViewModel.updateCreateField("deadline", it) },
                            label = { Text("截止时间 (例: 2026-07-30T18:00:00)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("允许弃权", modifier = Modifier.weight(1f))
                            Switch(
                                checked = voteState.createAllowAbstain,
                                onCheckedChange = { voteViewModel.updateCreateField("allowAbstain", it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("允许修改投票", modifier = Modifier.weight(1f))
                            Switch(
                                checked = voteState.createAllowChangeVote,
                                onCheckedChange = { voteViewModel.updateCreateField("allowChangeVote", it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { voteViewModel.createVote() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !voteState.isLoading &&
                                    voteState.createTopic.isNotBlank() &&
                                    voteState.createFileNumber.isNotBlank() &&
                                    voteState.createVotePassword.isNotBlank() &&
                                    voteState.createDeadline.isNotBlank()
                        ) {
                            Text("发起投票")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun VotingCard(
    sessionId: Int,
    topic: String,
    allowAbstain: Boolean,
    allowChangeVote: Boolean,
    myVote: com.heima.vote.data.model.MyVoteInfo?,
    onSubmit: (String) -> Unit,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean
) {
    val canModify = allowChangeVote && myVote != null
    val hasVoted = myVote != null && !canModify

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text("投票：$topic", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("关闭") }
            }

            if (hasVoted) {
                Text(
                    "您已投票：${
                        when(myVote?.choice) {
                            "agree" -> "同意"
                            "oppose" -> "反对"
                            "abstain" -> "弃权"
                            else -> myVote?.choice
                        }
                    }",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                if (!allowChangeVote) {
                    Text("该投票不允许修改", style = MaterialTheme.typography.bodySmall, color = SubTextColor)
                }
            }

            if (!hasVoted || canModify) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (canModify) onChange("agree") else onSubmit("agree") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AgreeColor),
                        enabled = !isLoading
                    ) { Text("同意") }

                    Button(
                        onClick = { if (canModify) onChange("oppose") else onSubmit("oppose") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = OpposeColor),
                        enabled = !isLoading
                    ) { Text("反对") }

                    if (allowAbstain) {
                        Button(
                            onClick = { if (canModify) onChange("abstain") else onSubmit("abstain") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AbstainColor),
                            enabled = !isLoading
                        ) { Text("弃权") }
                    }
                }
            }
        }
    }
}
