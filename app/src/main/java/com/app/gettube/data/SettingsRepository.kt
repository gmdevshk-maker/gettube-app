package com.app.gettube.data

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import com.app.gettube.R
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.VideoQuality
import java.io.File

/** 다크 모드 설정값. SYSTEM은 OS 설정을 따른다. */
enum class DarkMode(val labelRes: Int) {
    SYSTEM(R.string.dark_mode_system),
    LIGHT(R.string.dark_mode_off),
    DARK(R.string.dark_mode_on),
}

/** 사용자 옵션을 SharedPreferences에 저장한다(추가 의존성 불필요). */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gettube_settings", Context.MODE_PRIVATE)

    private val downloadsRoot: File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** 음악 기본값: <공용 Download>/GetTube/Music */
    val defaultAudioPath: String = File(downloadsRoot, "GetTube/Music").absolutePath

    /** 영상 기본값: <공용 Download>/GetTube/Video */
    val defaultVideoPath: String = File(downloadsRoot, "GetTube/Video").absolutePath

    var audioDownloadPath: String
        get() = prefs.getString(KEY_AUDIO_PATH, defaultAudioPath) ?: defaultAudioPath
        set(value) = prefs.edit { putString(KEY_AUDIO_PATH, value) }

    var videoDownloadPath: String
        get() = prefs.getString(KEY_VIDEO_PATH, defaultVideoPath) ?: defaultVideoPath
        set(value) = prefs.edit { putString(KEY_VIDEO_PATH, value) }

    var darkMode: DarkMode
        get() = runCatching { DarkMode.valueOf(prefs.getString(KEY_DARK, null) ?: "") }
            .getOrDefault(DarkMode.SYSTEM)
        set(value) = prefs.edit { putString(KEY_DARK, value.name) }

    var audioQuality: AudioQuality
        get() = runCatching { AudioQuality.valueOf(prefs.getString(KEY_AUDIO_Q, null) ?: "") }
            .getOrDefault(AudioQuality.MP3_192)
        set(value) = prefs.edit { putString(KEY_AUDIO_Q, value.name) }

    var videoQuality: VideoQuality
        get() = runCatching { VideoQuality.valueOf(prefs.getString(KEY_VIDEO_Q, null) ?: "") }
            .getOrDefault(VideoQuality.P1080)
        set(value) = prefs.edit { putString(KEY_VIDEO_Q, value.name) }

    private companion object {
        const val KEY_AUDIO_PATH = "audio_download_path"
        const val KEY_VIDEO_PATH = "video_download_path"
        const val KEY_DARK = "dark_mode"
        const val KEY_AUDIO_Q = "audio_quality"
        const val KEY_VIDEO_Q = "video_quality"
    }
}
