package com.app.gettube.download

import android.util.Log
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.MediaType
import com.app.gettube.model.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.UUID

enum class DownloadState { PREPARING, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/** 목록 상단에 표시되는, 진행 중(또는 방금 완료/실패한) 다운로드 1건. */
data class DownloadTask(
    val id: String,
    val title: String,
    val type: MediaType,
    val progress: Float = 0f,            // 0f..1f ; PREPARING 상태는 불확정 진행바 표시
    val state: DownloadState = DownloadState.PREPARING,
    val message: String? = null,
)

/**
 * youtubedl-android(yt-dlp)를 감싸는 얇은 래퍼. 진행 중 작업 목록을 [StateFlow]로 노출해
 * Compose가 진행도를 관찰할 수 있게 하고, 다운로드는 [Dispatchers.IO]에서 수행한다.
 *
 * 인스턴스는 [com.app.gettube.GetTubeApp] Application 객체에 하나만 존재한다.
 */
class DownloadManager {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** 사용자가 직접 취소한 ID 집합. 취소와 실제 오류를 구분하기 위해 사용한다. */
    private val cancelledIds = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * [url]을 오디오/영상으로 [destDir]에 다운로드한다. 완료·실패·취소될 때까지 suspend되며,
     * 성공한 경우에만 true를 반환한다. [audioQuality]/[videoQuality]로 품질을 지정한다.
     */
    suspend fun download(
        url: String,
        type: MediaType,
        destDir: File,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            _tasks.update { it + DownloadTask(id = id, title = "", type = type) }

            try {
                if (!destDir.exists()) destDir.mkdirs()

                // 진행 행에 보기 좋은 제목을 표시하기 위해 실제 영상 제목을 먼저 조회한다.
                val title = runCatching { YoutubeDL.getInstance().getInfo(url).title }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "YouTube ${if (type == MediaType.AUDIO) "Audio" else "Video"}"
                patch(id) { it.copy(title = title, state = DownloadState.DOWNLOADING) }

                val request = YoutubeDLRequest(url).apply {
                    // %(title)s.%(ext)s -> 대상 폴더에 사람이 읽기 좋은 파일명으로 저장
                    addOption("-o", File(destDir, "%(title)s.%(ext)s").absolutePath)
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    // YouTube SABR 스트리밍(HTTP 403) 우회: SABR 전용 스트림을 주는 기본 web
                    // 클라이언트 대신, 아직 직접 미디어 URL을 노출하는 "tv"/"web_safari"
                    // 클라이언트에서 포맷을 가져온다.
                    addOption("--extractor-args", "youtube:player_client=default,tv,web_safari")
                    if (type == MediaType.AUDIO) {
                        addOption("-x")                       // 오디오만 추출
                        when (audioQuality) {
                            AudioQuality.MP3_192 -> {
                                addOption("--audio-format", "mp3")
                                addOption("--audio-quality", "192K") // 192 kbps MP3
                            }
                            AudioQuality.ORIGINAL -> {
                                // 원본 코덱 유지(무손실 추출, 보통 .m4a/.opus)
                                // addOption("--audio-format", "best")
                                addOption("--audio-format", "mp3")
                                addOption("--audio-quality", "0")
                            }
                        }
                    } else {
                        // 선택한 해상도 상한 이하의 최고 영상 + 최고 음성을 MKV로 무손실 병합한다.
                        // MKV는 어떤 영상/음성 코덱 조합도 수용하므로 재포장/품질 손실이 없다.
                        val format = when (videoQuality) {
                            VideoQuality.P1080 ->
                                "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                            VideoQuality.P720 ->
                                "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                            VideoQuality.ORIGINAL ->
                                "bestvideo+bestaudio/best" // 해상도 제한 없음(최고 화질)
                        }
                        addOption("-f", format)
                        addOption("--merge-output-format", "mkv")
                    }
                }

                YoutubeDL.getInstance().execute(request, id) { progress, _, _ ->
                    if (progress >= 0f) patch(id) { it.copy(progress = progress / 100f) }
                }

                patch(id) { it.copy(progress = 1f, state = DownloadState.COMPLETED) }
                remove(id)   // 성공 -> 디스크 목록 스캔으로 파일이 나타남
                true
            } catch (e: InterruptedException) {
                cancelledIds.remove(id)
                remove(id)
                false
            } catch (e: Exception) {
                if (cancelledIds.remove(id)) {
                    // destroyProcessById가 execute()를 예외로 던지게 함 -> 정상 취소로 처리
                    remove(id)
                } else {
                    Log.e(TAG, "Download failed for $url", e)
                    patch(id) {
                        it.copy(state = DownloadState.FAILED, message = e.message ?: "다운로드 실패")
                    }
                }
                false
            }
        }

    /** 진행 중인 다운로드를 취소한다. 실행 중이던 [download] 호출이 CANCELLED로 정리된다. */
    fun cancel(id: String) {
        cancelledIds.add(id)
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    /** 완료/실패한 작업 행을 목록에서 제거한다(닫기 버튼에서 사용). */
    fun dismiss(id: String) = remove(id)

    private fun patch(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    private companion object {
        const val TAG = "GetTube/Download"
    }
}
