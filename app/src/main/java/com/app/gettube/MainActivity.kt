package com.app.gettube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.gettube.ui.MainScreen
import com.app.gettube.ui.MainViewModel
import com.app.gettube.ui.SettingsScreen
import com.app.gettube.ui.UpdateDialog
import com.app.gettube.ui.theme.GetTubeTheme

class MainActivity : ComponentActivity() {

    /** 공유(ACTION_SEND) 등으로 전달된 URL. 입력창에 자동으로 채우기 위해 관찰한다. */
    private val incomingUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUrl.value = extractUrl(intent)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()

            // 공유로 들어온 URL을 입력창에 한 번 채우고 소비한다.
            val shared = incomingUrl.value
            LaunchedEffect(shared) {
                if (!shared.isNullOrBlank()) {
                    vm.onUrlChange(shared)
                    incomingUrl.value = null
                }
            }

            GetTubeTheme(darkMode = vm.darkMode) {
                AppRoot(vm)
            }
        }
    }

    /** 앱이 이미 떠 있을 때(singleTop) 공유로 다시 진입하면 새 인텐트를 받는다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl.value = extractUrl(intent)
    }
}

/** 공유/뷰 인텐트에서 첫 번째 URL을 추출한다. 없으면 원문 텍스트를 그대로 반환. */
private fun extractUrl(intent: Intent?): String? {
    if (intent == null) return null
    val raw = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    } ?: return null
    // YouTube 공유 텍스트에는 제목 등이 섞일 수 있으므로 URL만 골라낸다.
    val urlRegex = Regex("""https?://\S+""")
    return urlRegex.find(raw)?.value ?: raw.trim().ifBlank { null }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else {
        MainScreen(vm = vm, onOpenSettings = { showSettings = true })
    }

    // 시작 시 확인된 앱(APK) 업데이트가 있으면 어느 화면 위에서든 안내한다.
    val updateState by vm.updateState.collectAsState()
    UpdateDialog(
        state = updateState,
        onConfirm = vm::startAppUpdate,
        onDismiss = vm::dismissAppUpdate,
    )
}
