package com.scriverse.app.feature.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scriverse.app.AppUiState
import com.scriverse.app.core.designsystem.SectionCard
import com.scriverse.app.core.model.WorkspaceProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    profile: WorkspaceProfile?,
    state: AppUiState,
    onLogin: (String, CharArray, String) -> Unit,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录 ${profile?.name.orEmpty()}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("软件内登录", style = MaterialTheme.typography.headlineMedium)
            Text(
                "密码只用于本次 HTTPS 请求，不会落盘。Server 返回的 App 会话由 Android Keystore 加密。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard("账号验证") {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { captcha = it },
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Button(
                    onClick = {
                        val passwordChars = password.toCharArray()
                        password = ""
                        onLogin(username, passwordChars, captcha)
                    },
                    enabled = !state.isLoggingIn && username.isNotBlank() && password.isNotBlank() && captcha.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    if (state.isLoggingIn) CircularProgressIndicator()
                    Text(if (state.isLoggingIn) "正在登录" else "登录")
                }
            }
            state.loginMessage?.let {
                Text(it, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
