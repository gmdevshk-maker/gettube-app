package com.app.gettube.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.app.gettube.R
import java.io.File

/**
 * 내장 저장소를 트리 형태로 탐색하며 다운로드 폴더를 선택하는 다이얼로그.
 *
 * SAF(content:// URI) 대신 실제 [File] 경로를 반환하므로 yt-dlp 네이티브 프로세스가 그대로 쓸 수 있다.
 * "모든 파일 접근" 권한이 있어야 하위 폴더가 보인다.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    initialPath: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val root = remember { Environment.getExternalStorageDirectory() }

    // 시작 위치: 기존 경로가 있으면 그곳, 없으면 상위 폴더, 그것도 없으면 루트.
    var current by remember {
        val start = File(initialPath).let { dir ->
            when {
                dir.isDirectory -> dir
                dir.parentFile?.isDirectory == true -> dir.parentFile!!
                else -> root
            }
        }
        mutableStateOf(start)
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showNewFolder by remember { mutableStateOf(false) }

    val subDirs = remember(current, refreshKey) {
        current.listFiles { f -> f.isDirectory && !f.isHidden }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }
    val canGoUp = current.absolutePath != root.absolutePath &&
        current.parentFile?.absolutePath?.startsWith(root.absolutePath) == true

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {

                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.size(10.dp))

                // 현재 경로 + 상위로 이동
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "상위 폴더",
                        tint = if (canGoUp) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(6.dp)
                            .then(
                                if (canGoUp) Modifier.clickable {
                                    current.parentFile?.let { current = it }
                                } else Modifier,
                            ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = displayPath(current, root),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )

                // 하위 폴더 트리
                if (subDirs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "하위 폴더가 없습니다",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(subDirs, key = { it.absolutePath }) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { current = dir }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = dir.name,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // 새 폴더 만들기
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNewFolder = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "새 폴더 만들기",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // 하단 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = { onSelect(current.absolutePath) }) {
                        Text("이 폴더 선택", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onConfirm = { name ->
                val created = File(current, name.trim())
                if (created.mkdirs() || created.isDirectory) {
                    current = created
                    refreshKey++
                }
                showNewFolder = false
            },
            onDismiss = { showNewFolder = false },
        )
    }
}

@Composable
private fun NewFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 폴더 만들기") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("폴더 이름") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
            ) { Text("만들기", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 내장 저장소 루트를 "내장 저장소"로 치환해 보기 좋은 경로 문자열로 만든다. */
private fun displayPath(dir: File, root: File): String {
    val rootPath = root.absolutePath
    val path = dir.absolutePath
    return if (path == rootPath) {
        "내장 저장소"
    } else if (path.startsWith("$rootPath/")) {
        "내장 저장소" + path.removePrefix(rootPath)
    } else {
        path
    }
}
