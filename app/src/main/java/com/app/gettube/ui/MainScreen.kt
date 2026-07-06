package com.app.gettube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.app.gettube.R
import com.app.gettube.download.DownloadState
import com.app.gettube.download.DownloadTask
import com.app.gettube.model.DownloadFile
import com.app.gettube.model.MediaType
import com.app.gettube.model.SortOrder
import com.app.gettube.ui.theme.AudioGreen
import com.app.gettube.ui.theme.AudioGreenBg
import com.app.gettube.ui.theme.AudioGreenBorder
import com.app.gettube.ui.theme.LoadingAmber
import com.app.gettube.ui.theme.LoadingAmberBg
import com.app.gettube.ui.theme.VideoBlue
import com.app.gettube.ui.theme.VideoBlueBg
import com.app.gettube.ui.theme.VideoBlueBorder
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsState()
    val engineReady by vm.engineReady.collectAsState()
    val permission = rememberStoragePermissionState()

    var fileToDelete by remember { mutableStateOf<DownloadFile?>(null) }
    var fileToRename by remember { mutableStateOf<DownloadFile?>(null) }
    val emptyUrlMsg = stringResource(R.string.empty_url)

    // 새 다운로드가 추가되면 목록을 최상단으로 스크롤해 진행 상태가 바로 보이게 한다.
    val listState = rememberLazyListState()
    var prevTaskCount by remember { mutableStateOf(0) }
    LaunchedEffect(tasks.size) {
        if (tasks.size > prevTaskCount) listState.animateScrollToItem(0)
        prevTaskCount = tasks.size
    }

    // 일회성 검증 메시지를 토스트로 표시.
    LaunchedEffect(vm.transientMessage) {
        vm.transientMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            HeaderSection(
                url = vm.url,
                onUrlChange = vm::onUrlChange,
                onOpenSettings = onOpenSettings,
            )

            DownloadButtonsRow(
                enabled = engineReady,
                onAudio = { vm.startDownload(MediaType.AUDIO, emptyUrlMsg) },
                onVideo = { vm.startDownload(MediaType.VIDEO, emptyUrlMsg) },
            )

            if (!engineReady) {
                InfoBanner(
                    text = stringResource(R.string.engine_loading),
                    color = LoadingAmber,
                    background = LoadingAmberBg,
                    showSpinner = true,
                )
            }
            if (!permission.granted) {
                InfoBanner(
                    text = stringResource(R.string.perm_banner),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    background = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.Warning,
                    onClick = permission.request,
                )
            }

            SectionHeader(
                count = tasks.size + vm.files.size,
                sortOrder = vm.sortOrder,
                onSortChange = vm::onSortChange,
            )

            FileList(
                listState = listState,
                tasks = tasks,
                files = vm.files,
                onCancelTask = vm::cancelDownload,
                onRetryTask = vm::retryDownload,
                onOpenFile = { openFileInPlayer(context, it.path, it.type) },
                onRenameFile = { fileToRename = it },
                onDeleteFile = { fileToDelete = it },
            )
        }
    }

    fileToDelete?.let { file ->
        DeleteConfirmDialog(
            fileName = file.name,
            onConfirm = {
                vm.deleteFile(file)
                fileToDelete = null
            },
            onDismiss = { fileToDelete = null },
        )
    }

    fileToRename?.let { file ->
        RenameDialog(
            currentName = file.name,
            onConfirm = { newBaseName ->
                vm.renameFile(file, newBaseName)
                fileToRename = null
            },
            onDismiss = { fileToRename = null },
        )
    }
}

@Composable
private fun HeaderSection(
    url: String,
    onUrlChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "GetTube",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.menu_settings),
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onOpenSettings),
                )
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = url,
                onValueChange = onUrlChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.url_hint), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFFAAAAAA))
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color(0xFF333333),
                    unfocusedTextColor = Color(0xFF333333),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DownloadButtonsRow(
    enabled: Boolean,
    onAudio: () -> Unit,
    onVideo: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DownloadButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.download_audio),
                icon = Icons.Filled.Headphones,
                content = AudioGreen,
                background = AudioGreenBg,
                border = AudioGreenBorder,
                enabled = enabled,
                onClick = onAudio,
            )
            DownloadButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.download_video),
                icon = Icons.Filled.Videocam,
                content = VideoBlue,
                background = VideoBlueBg,
                border = VideoBlueBorder,
                enabled = enabled,
                onClick = onVideo,
            )
        }
    }
}

@Composable
private fun DownloadButton(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    content: Color,
    background: Color,
    border: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .background(background.copy(alpha = alpha), RoundedCornerShape(9.dp))
            .border(1.dp, border.copy(alpha = alpha), RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = content.copy(alpha = alpha), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeader(
    count: Int,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.files_header, count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(sortOrder.labelRes),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(stringResource(order.labelRes)) },
                        onClick = {
                            onSortChange(order)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    tasks: List<DownloadTask>,
    files: List<DownloadFile>,
    onCancelTask: (String) -> Unit,
    onRetryTask: (DownloadTask) -> Unit,
    onOpenFile: (DownloadFile) -> Unit,
    onRenameFile: (DownloadFile) -> Unit,
    onDeleteFile: (DownloadFile) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (tasks.isEmpty() && files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_files),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            return@Surface
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(tasks, key = { it.id }) { task ->
                TaskRow(task = task, onCancel = onCancelTask, onRetry = onRetryTask)
                RowDivider()
            }
            items(files, key = { it.path }) { file ->
                FileRow(file = file, onOpen = onOpenFile, onRename = onRenameFile, onDelete = onDeleteFile)
                RowDivider()
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    onCancel: (String) -> Unit,
    onRetry: (DownloadTask) -> Unit,
) {
    val failed = task.state == DownloadState.FAILED
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            background = if (failed) Color(0x22D32F2F) else LoadingAmberBg,
            content = if (failed) MaterialTheme.colorScheme.primary else LoadingAmber,
            icon = if (failed) Icons.Filled.Warning else null,
            showSpinner = !failed,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title.ifBlank { stringResource(R.string.preparing) },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(if (task.type == MediaType.AUDIO) "MP3" else "MKV", task.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        failed -> task.message ?: "다운로드 실패"
                        task.state == DownloadState.PREPARING -> stringResource(R.string.preparing)
                        else -> "${(task.progress * 100).toInt()}%"
                    },
                    fontSize = 11.sp,
                    color = if (failed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!failed) {
                Spacer(Modifier.height(5.dp))
                if (task.state == DownloadState.PREPARING) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = LoadingAmber,
                        trackColor = LoadingAmberBg,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = LoadingAmber,
                        trackColor = LoadingAmberBg,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (failed) {
            // 실패 시에는 닫기 대신 같은 URL로 다시 시도하는 새로고침 버튼을 노출한다.
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.action_retry),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onRetry(task) },
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_cancel),
                tint = LoadingAmber,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onCancel(task.id) },
            )
        }
    }
}

@Composable
private fun FileRow(
    file: DownloadFile,
    onOpen: (DownloadFile) -> Unit,
    onRename: (DownloadFile) -> Unit,
    onDelete: (DownloadFile) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isAudio = file.type == MediaType.AUDIO
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(file) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            background = if (isAudio) AudioGreenBg else VideoBlueBg,
            content = if (isAudio) AudioGreen else VideoBlue,
            icon = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(extensionBadge(file.name), file.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatSize(file.sizeBytes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDate(file.lastModified),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Box {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename(file)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete(file)
                    },
                )
            }
        }
    }
}

@Composable
private fun IconBadge(
    background: Color,
    content: Color,
    icon: ImageVector? = null,
    showSpinner: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(background, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showSpinner -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = content,
                strokeWidth = 2.dp,
            )
            icon != null -> Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TypeBadge(label: String, type: MediaType) {
    val isAudio = type == MediaType.AUDIO
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = if (isAudio) AudioGreen else VideoBlue,
        modifier = Modifier
            .background(if (isAudio) AudioGreenBg else VideoBlueBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun InfoBanner(
    text: String,
    color: Color,
    background: Color,
    icon: ImageVector? = null,
    showSpinner: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = color, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_msg, fileName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 확장자를 제외한 현재 이름을 초기값으로(예: "노래.mp3" -> "노래").
    val baseName = remember(currentName) { currentName.substringBeforeLast('.') }
    var text by remember { mutableStateOf(baseName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.rename_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
            ) {
                Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
