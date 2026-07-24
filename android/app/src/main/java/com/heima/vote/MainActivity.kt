package com.heima.vote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.heima.vote.ui.screens.AdminScreen
import com.heima.vote.ui.screens.HomeScreen
import com.heima.vote.ui.screens.LoginScreen
import com.heima.vote.ui.theme.HeimaVoteTheme
import com.heima.vote.viewmodel.AdminViewModel
import com.heima.vote.viewmodel.AuthViewModel
import com.heima.vote.viewmodel.VoteViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val voteViewModel: VoteViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HeimaVoteTheme {
                val authState by authViewModel.uiState.collectAsState()

                if (!authState.isLoggedIn) {
                    // 未登录 → 登录页
                    LoginScreen(authViewModel = authViewModel)
                } else {
                    // 简单路由：通过状态控制页面切换
                    var showAdmin by remember { mutableStateOf(false) }

                    if (showAdmin) {
                        AdminScreen(
                            authViewModel = authViewModel,
                            voteViewModel = voteViewModel,
                            adminViewModel = adminViewModel,
                            onBack = { showAdmin = false }
                        )
                    } else {
                        HomeScreen(
                            authViewModel = authViewModel,
                            voteViewModel = voteViewModel,
                            onNavigateToAdmin = { showAdmin = true }
                        )
                    }
                }
            }
        }
    }
}
