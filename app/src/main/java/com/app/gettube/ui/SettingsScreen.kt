package com.app.gettube.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.gettube.R
import com.app.gettube.data.DarkMode
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.MediaType
import com.app.gettube.model.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    // 시스템 back 버튼: 앱 종료 대신 이전(첫) 화면으로 돌아간다.
    BackHandler(onBack = onBack)

    // 폴더 선택 대상(음악/영상). null이면 다이얼로그 닫힘.
    var pickingFor by remember { mutableStateOf<MediaType?>(null) }
    var updatingEngine by remember { mutableStateOf(false) }
    val engineVersion by vm.engineVersion.collectAsState()
    val context = LocalContext.current
    val updatingText = stringResource(R.string.engine_updating)
    // 현재 설치된 앱의 versionName(build.gradle의 versionName). 조회 실패 시 빈 문자열.
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(24.dp)
                            .clickable(onClick = onBack),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // 다운로드 위치 (음악/영상)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.settings_download_location),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PathRow(
                title = stringResource(R.string.label_audio),
                path = vm.audioDownloadPath,
                onClick = { pickingFor = MediaType.AUDIO },
            )
            PathRow(
                title = stringResource(R.string.label_video),
                path = vm.videoDownloadPath,
                onClick = { pickingFor = MediaType.VIDEO },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 다운로드 품질
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.settings_quality),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            QualityRow(
                title = stringResource(R.string.label_audio),
                currentLabel = stringResource(vm.audioQuality.labelRes),
                options = AudioQuality.entries.map { it to stringResource(it.labelRes) },
                onSelect = { vm.updateAudioQuality(it) },
            )
            QualityRow(
                title = stringResource(R.string.label_video),
                currentLabel = stringResource(vm.videoQuality.labelRes),
                options = VideoQuality.entries.map { it to stringResource(it.labelRes) },
                onSelect = { vm.updateVideoQuality(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 다크 모드 (드롭다운)
            DropdownSettingRow(
                icon = { Icon(Icons.Filled.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.settings_dark_mode),
                currentLabel = stringResource(vm.darkMode.labelRes),
                options = DarkMode.entries.map { it to stringResource(it.labelRes) },
                onSelect = { vm.updateDarkMode(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // 다운로드 엔진(yt-dlp) — 수동 업데이트 + 현재 버전 표시
            SettingRow(
                icon = { Icon(Icons.Filled.Update, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.settings_engine),
                subtitle = if (updatingEngine) {
                    updatingText
                } else {
                    val v = engineVersion?.let { "yt-dlp $it" } ?: ""
                    if (v.isEmpty()) stringResource(R.string.settings_engine_update_hint)
                    else "$v · ${stringResource(R.string.settings_engine_update_hint)}"
                },
                onClick = {
                    if (!updatingEngine) {
                        updatingEngine = true
                        Toast.makeText(context, updatingText, Toast.LENGTH_SHORT).show()
                        vm.updateEngine { result ->
                            updatingEngine = false
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )

            // 맨 하단에 현재 앱 버전을 표시한다.
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.settings_app_version, appVersion),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }

    pickingFor?.let { target ->
        val isAudio = target == MediaType.AUDIO
        FolderPickerDialog(
            title = stringResource(
                if (isAudio) R.string.settings_audio_location else R.string.settings_video_location,
            ),
            initialPath = if (isAudio) vm.audioDownloadPath else vm.videoDownloadPath,
            onSelect = {
                if (isAudio) vm.updateAudioPath(it) else vm.updateVideoPath(it)
                pickingFor = null
            },
            onDismiss = { pickingFor = null },
        )
    }
}

/** 아이콘 + 제목(좌) + 현재값 + 드롭다운(우) 한 줄. 단일 설정(다크 모드 등)에 사용. */
@Composable
private fun <T> DropdownSettingRow(
    icon: @Composable () -> Unit,
    title: String,
    currentLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // 드롭다운은 이 트리거(값 + 화살표) 바로 아래로 펼쳐진다.
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 라벨(좌) + 현재 경로(우, 말줄임) 한 줄. 탭하면 폴더 선택기를 연다. 다운로드 위치에 사용. */
@Composable
private fun PathRow(
    title: String,
    path: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Text(
            path,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 라벨 + 현재값 + 드롭다운(탐색기 메뉴 형태) 한 줄. 다운로드 품질 선택에 사용. */
@Composable
private fun <T> QualityRow(
    title: String,
    currentLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(start = 56.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // 드롭다운은 이 트리거(값 + 화살표) 바로 아래로 펼쳐진다.
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(2.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

