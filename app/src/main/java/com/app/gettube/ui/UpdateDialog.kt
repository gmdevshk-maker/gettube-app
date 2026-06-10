package com.app.gettube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.app.gettube.R
import com.app.gettube.update.UpdateInfo
import com.app.gettube.update.UpdateState

/**
 * 앱(APK) 업데이트 상태에 따른 다이얼로그. [UpdateState.Idle]에서는 아무것도 그리지 않는다.
 *  - Available  : 새 버전 안내 + "업데이트"/"나중에"
 *  - Downloading: 진행 다이얼로그(취소 불가)
 *  - Failed     : 실패 안내 + "닫기"
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onConfirm: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Idle -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_msg, state.info.version)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(state.info) }) {
                    Text(
                        stringResource(R.string.update_action_now),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_action_later))
                }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            // 다운로드 중에는 바깥 탭/뒤로가기로 닫히지 않게 한다.
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(state.progress * 100).toInt()}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.update_action_close),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}
