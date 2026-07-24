package com.heima.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heima.vote.data.model.VoteDetailResponse
import com.heima.vote.data.model.VoteSessionItem
import com.heima.vote.ui.theme.*
import com.heima.vote.viewmodel.AdminViewModel
import com.heima.vote.viewmodel.AuthViewModel
import com.heima.vote.viewmodel.VoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    authViewModel: AuthViewModel,
    voteViewModel: VoteViewModel,
    adminViewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val voteState by voteViewModel.uiState.collectAsState()
    val adminState by adminViewModel.uiState.collectAsState()

    val isSuperAdmin = authState.role == "super_admin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理面板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            // 消息/错误提示
            adminState.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = AgreeColor.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = AgreeColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it)
                    }
                }
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(3000)
                    adminViewModel.clearMessage()
                }
            }
            adminState.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = OpposeColor.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Filled.Error, null, tint = OpposeColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, color = OpposeColor)
                    }
                }
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(3000)
                    adminViewModel.clearMessage()
                }
            }

            // ===== 1. 数据汇总 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Assessment, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("数据汇总", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { voteViewModel.loadSessions() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("刷新投票列表") }

                    if (voteState.sessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        voteState.sessions.forEach { session ->
                            VoteSessionItemCard(
                                session = session,
                                onViewDetail = { voteViewModel.loadVoteDetail(session.id) },
                                isExpanded = voteState.voteDetail?.id == session.id,
                                detail = voteState.voteDetail
                            )
                        }
                    }
                }
            }

            // ===== 2. 创建用户（仅超管） =====
            if (isSuperAdmin) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("创建用户", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = adminState.newUsername,
                            onValueChange = { adminViewModel.updateField("newUsername", it) },
                            label = { Text("新账号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = adminState.newPassword,
                            onValueChange = { adminViewModel.updateField("newPassword", it) },
                            label = { Text("新密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 角色选择
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("角色: ", modifier = Modifier.padding(end = 8.dp))
                            FilterChip(
                                selected = adminState.newRole == "user",
                                onClick = { adminViewModel.updateField("newRole", "user") },
                                label = { Text("普通用户") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = adminState.newRole == "admin",
                                onClick = { adminViewModel.updateField("newRole", "admin") },
                                label = { Text("管理员") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { adminViewModel.createUser() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !adminState.isLoading &&
                                    adminState.newUsername.isNotBlank() &&
                                    adminState.newPassword.isNotBlank()
                        ) { Text("创建用户") }

                        // 用户列表
                        if (adminState.users.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("现有用户:", style = MaterialTheme.typography.bodyMedium)
                            adminState.users.forEach { user ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(user.username, modifier = Modifier.weight(1f))
                                    Text(
                                        when (user.role) {
                                            "super_admin" -> "超管"
                                            "admin" -> "管理员"
                                            else -> "用户"
                                        },
                                        color = SubTextColor
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // ===== 3. 修改密钥 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("修改解锁密钥", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = adminState.oldKey,
                        onValueChange = { adminViewModel.updateField("oldKey", it) },
                        label = { Text("旧密钥") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = adminState.newKey,
                        onValueChange = { adminViewModel.updateField("newKey", it) },
                        label = { Text("新密钥（至少6位）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { adminViewModel.changeSecretKey() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !adminState.isLoading &&
                                adminState.oldKey.isNotBlank() &&
                                adminState.newKey.length >= 6
                    ) { Text("修改密钥") }
                }
            }
        }
    }
}

@Composable
fun VoteSessionItemCard(
    session: VoteSessionItem,
    onViewDetail: () -> Unit,
    isExpanded: Boolean,
    detail: VoteDetailResponse?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.topic, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "文件: ${session.fileNumber}  |  截止: ${session.deadline.take(16).replace("T", " ")}  |  已投: ${session.voteCount ?: 0}票",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubTextColor
                    )
                }
                TextButton(onClick = onViewDetail) {
                    Text(if (isExpanded) "收起" else "详情")
                }
            }

            // 展开汇总
            if (isExpanded && detail != null) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SummaryContent(detail)
            }
        }
    }
}

@Composable
fun SummaryContent(detail: VoteDetailResponse) {
    Column {
        Text("投票汇总", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        val s = detail.summary
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("同意", s.agree.count, s.agree.percent, AgreeColor)
            StatItem("反对", s.oppose.count, s.oppose.percent, OpposeColor)
            StatItem("弃权", s.abstain.count, s.abstain.percent, AbstainColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("总投票人数: ${s.total}", style = MaterialTheme.typography.bodyMedium)

        if (detail.details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("投票明细:", style = MaterialTheme.typography.bodySmall)
            detail.details.forEach { d ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(d.username, modifier = Modifier.width(80.dp))
                    Text(
                        when (d.choice) {
                            "agree" -> "同意"
                            "oppose" -> "反对"
                            "abstain" -> "弃权"
                            else -> d.choice
                        },
                        color = when (d.choice) {
                            "agree" -> AgreeColor
                            "oppose" -> OpposeColor
                            else -> AbstainColor
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(d.votedAt.take(16).replace("T", " "), style = MaterialTheme.typography.bodySmall, color = SubTextColor)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, percent: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
        Text("$label ($percent)", style = MaterialTheme.typography.bodySmall)
    }
}
