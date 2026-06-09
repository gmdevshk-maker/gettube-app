package com.app.gettube

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.app.gettube.download.DownloadManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Application 싱글턴. 네이티브 yt-dlp + ffmpeg 엔진을 (요구사항대로 메인 스레드 밖에서) 한 번
 * 초기화하고, 공용 [DownloadManager]를 소유한다.
 *
 * 라이브러리에 번들된 yt-dlp 바이너리는 금방 낡는다 — YouTube 변경으로 구버전이 깨진다
 * (예: SABR 스트리밍 -> HTTP 403). 그래서 시작 시 다운로드를 활성화하기 전에, [UPDATE_INTERVAL_MS]
 * 간격으로 최대 1회 yt-dlp를 최신 nightly로 자체 업데이트하고, 설정에서 수동 업데이트도 제공한다.
 */
class GetTubeApp : Application() {

    val downloadManager = DownloadManager()

    private val _engineReady = MutableStateFlow(false)

    /** yt-dlp + ffmpeg가 준비되고(최근 자체 업데이트 시도 포함) 다운로드가 가능하면 true. */
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>(null)

    /** 현재 설치된 yt-dlp 버전(설정 화면에 표시). */
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@GetTubeApp)
                FFmpeg.getInstance().init(this@GetTubeApp)
                refreshVersion()
                if (isUpdateDue()) updateEngine()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise download engine", e)
            } finally {
                // 초기화/업데이트가 일부 실패해도 다운로드는 시도할 수 있게 둔다.
                _engineReady.value = true
            }
        }
    }

    /**
     * yt-dlp를 최신 nightly로 업데이트한다(수동 버튼/자동 공통). 결과 메시지를 반환한다.
     * 네트워크가 필요하며, 실패해도 예외를 던지지 않고 메시지로 알린다.
     *
     * [force]가 true이면 현재 설치 버전이 최신이어도 다시 받아 덮어쓴다. 라이브러리는
     * 저장된 버전과 최신 릴리스 tag가 같으면 다운로드를 건너뛰므로(ALREADY_UP_TO_DATE),
     * 강제 시에는 라이브러리가 참조하는 버전 키를 미리 비워 버전 불일치 상태로 만든다.
     */
    suspend fun updateEngine(force: Boolean = false): String = withContext(Dispatchers.IO) {
        // 강제일 때만 백업/초기화. 다운로드 실패 시 되돌리기 위해 기존 값을 보관한다.
        val savedVersion = if (force) ytdlpPrefs().getString(KEY_DLP_VERSION, null) else null
        if (force) ytdlpPrefs().edit { remove(KEY_DLP_VERSION) }
        try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(this@GetTubeApp, YoutubeDL.UpdateChannel.NIGHTLY)
            prefs().edit { putLong(KEY_LAST_UPDATE, System.currentTimeMillis()) }
            refreshVersion()
            Log.i(TAG, "yt-dlp update: $status, version=${_engineVersion.value}")
            "업데이트 완료: ${status?.name ?: "DONE"} (yt-dlp ${_engineVersion.value ?: "?"})"
        } catch (e: Exception) {
            // 다운로드 실패 시: 비워둔 버전 키를 복원해 표시/게이트 상태를 유지한다.
            if (force && savedVersion != null) {
                ytdlpPrefs().edit { putString(KEY_DLP_VERSION, savedVersion) }
                refreshVersion()
            }
            Log.w(TAG, "yt-dlp update failed", e)
            "업데이트 실패: ${e.message ?: "네트워크를 확인하세요"}"
        }
    }

    private fun refreshVersion() {
        _engineVersion.value = runCatching { YoutubeDL.getInstance().version(this) }.getOrNull()
    }

    private fun isUpdateDue(): Boolean =
        System.currentTimeMillis() - prefs().getLong(KEY_LAST_UPDATE, 0L) >= UPDATE_INTERVAL_MS

    private fun prefs() = getSharedPreferences("gettube_engine", Context.MODE_PRIVATE)

    /** youtubedl-android 라이브러리가 설치 버전을 저장하는 prefs(강제 재설치 시 버전 키를 비우기 위함). */
    private fun ytdlpPrefs() = getSharedPreferences(YTDLP_PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "GetTube"
        const val KEY_LAST_UPDATE = "last_ytdlp_update"
        val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

        // 라이브러리 내부 SharedPrefsHelper와 동일해야 한다(youtubedl-android 0.18.1 기준).
        const val YTDLP_PREFS_NAME = "youtubedl-android"
        const val KEY_DLP_VERSION = "dlpVersion"
    }
}
