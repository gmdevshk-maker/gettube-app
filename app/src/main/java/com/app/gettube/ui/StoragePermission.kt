package com.app.gettube.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 공용 다운로드 폴더 쓰기 권한 상태 + 권한 요청 동작. */
data class StoragePermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * 선택한 다운로드 폴더에 쓸 수 있는지 추적하고 [request] 동작을 노출한다.
 *
 * - Android 11+ (API 30): "모든 파일 접근"(MANAGE_EXTERNAL_STORAGE)이 필요하며 시스템 설정
 *   화면에서 허용한다.
 * - Android 10 이하: 런타임 WRITE_EXTERNAL_STORAGE 권한이 필요하다.
 *
 * ON_RESUME마다 다시 확인하므로 사용자가 설정에서 돌아오면 배너가 사라진다.
 */
@Composable
fun rememberStoragePermissionState(): StoragePermissionState {
    val context = LocalContext.current

    fun currentlyGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    var granted by remember { mutableStateOf(currentlyGranted()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = currentlyGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    val request: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    return StoragePermissionState(granted, request)
}
