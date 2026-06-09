package com.app.gettube.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
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

/**
 * 다운로드한 파일을 시스템 기본 오디오/영상 플레이어로 연다.
 *
 * MediaStore content URI(content://media/...)로 연다. 미디어 프로바이더가 권한을 직접
 * 관리하므로 per-URI grant가 필요 없고, 플레이어가 기대하는 표준 미디어 URI라 호환성이 좋다.
 * 아직 색인되지 않았으면 안내만 표시한다(다운로드 시 MediaScanner가 등록한다).
 */
fun openFileInPlayer(context: Context, path: String, type: MediaType) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "파일을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = mediaStoreUri(context, file, type)
    if (uri == null) {
        Toast.makeText(context, "미디어 색인 준비 중입니다. 잠시 후 다시 시도하세요", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolveMimeType(file, type))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "재생할 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
}

/**
 * 파일 경로로 MediaStore의 content URI를 찾는다. 색인되어 있지 않으면 null.
 * DATA 컬럼은 쓰기에는 deprecated지만 경로로 조회하는 용도로는 현재도 유효하다.
 */
private fun mediaStoreUri(context: Context, file: File, type: MediaType): Uri? {
    val collection = when (type) {
        MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    return runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA}=?",
            arrayOf(file.absolutePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else {
                null
            }
        }
    }.getOrNull()
}

/**
 * 파일 확장자로 구체적인 MIME 타입을 만든다. content:// URI로 넘기면 플레이어가 확장자를 볼 수
 * 없어 MIME만으로 포맷을 판단하므로, 와일드카드 MIME면 바로 재생되지 않는 경우가 많다.
 * 흔한 컨테이너는 직접 매핑하고(기기별 MimeTypeMap 누락 대비), 없으면 시스템 매핑→와일드카드 순.
 */
private fun resolveMimeType(file: File, type: MediaType): String {
    val ext = file.extension.lowercase(Locale.ROOT)
    val explicit = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/x-wav"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        else -> null
    }
    if (explicit != null) return explicit
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return when (type) {
        MediaType.AUDIO -> "audio/*"
        MediaType.VIDEO -> "video/*"
    }
}

/** 파일 확장자 배지 텍스트(대문자), 예: "MP3" / "MKV". */
fun extensionBadge(name: String): String =
    name.substringAfterLast('.', "").uppercase(Locale.ROOT).ifEmpty { "FILE" }
