package com.app.gettube.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.app.gettube.model.MediaType
import java.io.File
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale

/** 사람이 읽기 좋은 파일 크기, 예: "8.3 MB". */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "${DecimalFormat("#,##0.#").format(value)} ${units[digitGroups]}"
}

/** 목업 스타일의 상대 날짜: "오늘 09:12", "어제", "06/05". */
fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }

    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 ->
            "오늘 %02d:%02d".format(then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
        sameYear && dayDiff == 1 -> "어제"
        sameYear -> "%02d/%02d".format(then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH))
        else -> "%d/%02d/%02d".format(
            then.get(Calendar.YEAR), then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH),
        )
    }
}

/** 다운로드한 파일을 FileProvider를 통해 시스템 기본 오디오/영상 플레이어로 연다. */
fun openFileInPlayer(context: Context, path: String, type: MediaType) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "파일을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mime = when (type) {
        MediaType.AUDIO -> "audio/*"
        MediaType.VIDEO -> "video/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "재생할 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
}

/** 파일 확장자 배지 텍스트(대문자), 예: "MP3" / "MKV". */
fun extensionBadge(name: String): String =
    name.substringAfterLast('.', "").uppercase(Locale.ROOT).ifEmpty { "FILE" }
