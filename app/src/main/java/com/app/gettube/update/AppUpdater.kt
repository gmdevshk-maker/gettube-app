package com.app.gettube.update

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 버전 서버가 내려주는 최신 릴리스 정보(JSON: packageName, version, apkUrl). */
data class UpdateInfo(
    val packageName: String,
    val version: String,
    val apkUrl: String,
)

/** 앱(APK) 자동 업데이트의 진행 상태. UI 다이얼로그가 관찰한다. */
sealed interface UpdateState {
    /** 확인 전이거나 최신이라 할 일이 없는 상태. */
    data object Idle : UpdateState

    /** 더 새 버전이 있어 사용자에게 설치 여부를 묻는 상태. */
    data class Available(val info: UpdateInfo) : UpdateState

    /** APK 다운로드 중. [progress]는 0f..1f, 전체 크기를 모르면 -1f(불확정). */
    data class Downloading(val progress: Float) : UpdateState

    /** 다운로드/설치 준비 실패. */
    data class Failed(val message: String) : UpdateState
}

/**
 * 앱 자체(APK) 업데이트 관리자. 시작 시 버전 API를 조회해 현재 설치 버전보다 최신이 있으면
 * 사용자에게 알리고, 동의하면 APK를 받아 시스템 설치 화면을 띄운다.
 *
 * yt-dlp 엔진 자체 업데이트([com.app.gettube.GetTubeApp.updateEngine])와는 별개로, 앱 바이너리
 * 전체를 새 버전으로 교체하는 흐름이다. 인스턴스는 Application에 하나만 존재한다.
 */
class AppUpdater(private val app: Application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * 버전 API를 조회해 현재 설치 버전보다 최신이면 [UpdateState.Available]로 전환한다.
     * 네트워크/파싱 실패는 앱 사용을 막지 않도록 조용히 무시한다.
     */
    suspend fun checkForUpdate() = withContext(Dispatchers.IO) {
        try {
            val info = fetchLatest() ?: return@withContext
            if (isNewer(info.version, currentVersionName())) {
                _state.value = UpdateState.Available(info)
            }
        } catch (e: Exception) {
            Log.w(TAG, "App update check failed", e)
        }
    }

    /** 사용자가 "업데이트"를 누르면 APK를 받아 설치 화면을 띄운다. 실패는 상태로 알린다. */
    suspend fun downloadAndInstall(info: UpdateInfo) = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Downloading(-1f)
        try {
            val apk = downloadApk(info.apkUrl)
            launchInstall(apk)
            // 설치 화면은 별도 시스템 UI로 뜨므로 우리 상태는 초기화한다.
            _state.value = UpdateState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "APK download/install failed", e)
            _state.value = UpdateState.Failed(e.message ?: "업데이트에 실패했습니다")
        }
    }

    /** 사용자가 "나중에"/"닫기"를 누르면 상태를 초기화한다. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** 버전 API를 호출해 [UpdateInfo]를 파싱한다. 응답이 유효하지 않으면 null. */
    private fun fetchLatest(): UpdateInfo? {
        val url = URL("$API_BASE?package=${app.packageName}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("version").trim()
            val apkUrl = json.optString("apkUrl").trim()
            if (version.isEmpty() || apkUrl.isEmpty()) return null
            return UpdateInfo(
                packageName = json.optString("packageName").trim(),
                version = version,
                apkUrl = apkUrl,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** apkUrl에서 APK를 캐시에 내려받으며 진행도를 [UpdateState.Downloading]으로 흘려보낸다. */
    private fun downloadApk(apkUrl: String): File {
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("서버 응답 오류(${conn.responseCode})")
            }
            val total = conn.contentLength.toLong()
            // 매번 새로 받으므로 이전 파일을 덮어쓴다(부분 파일 잔존 방지).
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            val apk = File(dir, "GetTube-update.apk")
            conn.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _state.value = UpdateState.Downloading(downloaded.toFloat() / total)
                        }
                    }
                }
            }
            return apk
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 받은 APK로 시스템 설치 화면을 띄운다. Android 7+는 file:// URI를 허용하지 않으므로
     * FileProvider content URI로 넘긴다. 출처를 알 수 없는 앱 설치 권한이 없으면 시스템이
     * 설치 화면에서 사용자를 설정으로 안내한다.
     */
    private fun launchInstall(apk: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    /** 현재 설치된 앱의 versionName(예: "1.0"). 조회 실패 시 빈 문자열. */
    private fun currentVersionName(): String =
        runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull().orEmpty()

    /**
     * "1.2.3" 형태의 버전 문자열을 점 단위 숫자로 비교해 [remote]가 [current]보다 큰지 판단한다.
     * 자릿수가 다르면 빈 자리는 0으로 본다(예: "1.1" > "1").
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private companion object {
        const val TAG = "GetTube/AppUpdate"
        const val API_BASE = "https://apk-update-server-gamma.vercel.app/api/version"
        const val TIMEOUT_MS = 10_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
    }
}
