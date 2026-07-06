package com.app.gettube.download

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.MediaType
import com.app.gettube.model.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.UUID

enum class DownloadState { PREPARING, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/** 목록 상단에 표시되는, 진행 중(또는 방금 완료/실패한) 다운로드 1건. */
data class DownloadTask(
    val id: String,
    val title: String,
    val type: MediaType,
    val url: String,                     // 재시도 시 다시 요청할 원본 URL
    val progress: Float = 0f,            // 0f..1f ; PREPARING 상태는 불확정 진행바 표시
    val state: DownloadState = DownloadState.PREPARING,
    val message: String? = null,
) {
    /** 아직 끝나지 않아 프로세스를 살려둬야 하는 상태인지. */
    val isActive: Boolean
        get() = state == DownloadState.PREPARING || state == DownloadState.DOWNLOADING
}

/**
 * youtubedl-android(yt-dlp)를 감싸는 얇은 래퍼. 진행 중 작업 목록을 [StateFlow]로 노출해
 * Compose가 진행도를 관찰할 수 있게 하고, 다운로드는 앱 수명 스코프의 [Dispatchers.IO]에서 수행한다.
 *
 * 실행 주체를 화면(ViewModel)이 아니라 이 매니저가 소유하므로, 화면이 백그라운드로 가거나
 * Activity가 재생성돼도 다운로드는 계속된다. 활성 다운로드가 있는 동안에는 [DownloadService]가
 * 포그라운드로 떠서 OS의 프로세스 종료를 막는다.
 *
 * 인스턴스는 [com.app.gettube.GetTubeApp] Application 객체에 하나만 존재한다.
 */
class DownloadManager(private val appContext: Context) {

    // 화면과 무관하게 살아있는 앱 수명 스코프. 개별 다운로드는 서로 독립적으로 실패해야 하므로 SupervisorJob.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** 다운로드가 완료되어 디스크 파일 목록이 바뀌었음을 UI에 알린다(목록 새로고침 트리거). */
    private val _fileListChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fileListChanged: SharedFlow<Unit> = _fileListChanged.asSharedFlow()

    /** 사용자가 직접 취소한 ID 집합. 취소와 실제 오류를 구분하기 위해 사용한다. */
    private val cancelledIds = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * [url]을 오디오/영상으로 [destDir]에 다운로드하도록 큐에 넣는다. 즉시 반환하며(대기하지 않음),
     * 진행 상태는 [tasks]로 관찰한다. 실행은 앱 스코프에서 이뤄지므로 화면과 무관하게 지속된다.
     */
    fun enqueue(
        url: String,
        type: MediaType,
        destDir: File,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
    ) {
        val id = UUID.randomUUID().toString()
        // 새 작업을 맨 앞에 넣어 목록 최상단에 표시(새 다운로드 상태가 바로 보이도록).
        _tasks.update { listOf(DownloadTask(id = id, title = "", type = type, url = url)) + it }
        // 진행 중에는 포그라운드 서비스가 프로세스/WakeLock을 유지한다. 서비스는 활성 작업이
        // 없어지면 스스로 종료하므로, 여기서는 시작만 보장한다.
        DownloadService.start(appContext)
        scope.launch { run(id, url, type, destDir, audioQuality, videoQuality) }
    }

    private suspend fun run(
        id: String,
        url: String,
        type: MediaType,
        destDir: File,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
    ) {
        try {
            if (!destDir.exists()) destDir.mkdirs()

            // 진행 행에 보기 좋은 제목을 표시하기 위해 실제 영상 제목을 먼저 조회한다.
            val title = runCatching { YoutubeDL.getInstance().getInfo(url).title }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "YouTube ${if (type == MediaType.AUDIO) "Audio" else "Video"}"
            patch(id) { it.copy(title = title, state = DownloadState.DOWNLOADING) }

            val request = buildRequest(url, type, destDir, audioQuality, videoQuality)

            YoutubeDL.getInstance().execute(request, id) { progress, _, _ ->
                if (progress >= 0f) patch(id) { it.copy(progress = progress / 100f) }
            }

            patch(id) { it.copy(progress = 1f, state = DownloadState.COMPLETED) }
            remove(id)   // 성공 -> 디스크 목록 스캔으로 파일이 나타남
            // 네이티브 프로세스가 파일을 직접 써서 MediaStore에 등록되지 않는다. 스캔을 걸어야
            // 다른 음악/영상 앱의 라이브러리(폴더 목록)에 파일이 보이고 재생도 된다.
            scanMedia(destDir)
            _fileListChanged.tryEmit(Unit)
        } catch (e: InterruptedException) {
            cancelledIds.remove(id)
            remove(id)
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
        }
    }

    private fun buildRequest(
        url: String,
        type: MediaType,
        destDir: File,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        // %(title)s.%(ext)s -> 대상 폴더에 사람이 읽기 좋은 파일명으로 저장
        addOption("-o", File(destDir, "%(title)s.%(ext)s").absolutePath)
        addOption("--no-mtime")
        addOption("--no-playlist")
        // YouTube 403 우회용 클라이언트 선택. 쿠키/PO 토큰을 쓰지 않으므로,
        // 토큰 없이도 동작하는 클라이언트만 쓴다:
        //  - web_safari : GVS PO 토큰 우회(SABR 회피)
        //  - web_embedded, android_vr : PO 토큰 불필요
        // "tv"/"tv_simply"는 쿠키 없으면 전 포맷이 DRM 처리되어 403이 나므로 제외한다
        // (yt-dlp #12563). 기본 web 클라이언트는 SABR로 포맷이 빠질 수 있어 뒤로 둔다.
        addOption(
            "--extractor-args",
            "youtube:player_client=web_safari,web_embedded,android_vr,default",
        )
        if (type == MediaType.AUDIO) {
            addOption("-x")                       // 오디오만 추출
            when (audioQuality) {
                AudioQuality.MP3_192 -> {
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K") // 192 kbps MP3
                }
                AudioQuality.ORIGINAL -> {
                    // 최고 음질 MP3(VBR V0, 대략 245kbps). 호환성을 위해 코덱은 mp3로 통일.
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
            }
            // 제목/업로더 등 메타데이터를 ID3 태그로 기록한다.
            addOption("--embed-metadata")
            // 유튜브 썸네일을 MP3 커버 아트(ID3 APIC)로 삽입한다(번들 ffmpeg 사용).
            // 커버는 JPEG로 변환하고, 16:9 썸네일을 중앙 정사각으로 크롭한다
            // (crop=ih:ih). 썸네일/임베드 실패는 yt-dlp가 경고만 내고 다운로드는 진행된다.
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
            addOption("--ppa", "ThumbnailsConvertor:-vf crop=ih:ih")
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
            // 제목/업로더 등 메타데이터(+챕터)를 MKV에 기록한다.
            addOption("--embed-metadata")
            // 유튜브 썸네일을 MKV 커버(첨부 이미지)로 삽입한다. 영상 커버는 16:9가
            // 자연스러우므로 MP3와 달리 정사각 크롭은 하지 않고, 호환을 위해 JPEG로 변환한다.
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
        }
    }

    /** 다운로드 폴더의 파일들을 MediaStore에 등록/갱신한다(외부 앱이 파일을 인식하도록). */
    private fun scanMedia(destDir: File) {
        val paths = destDir.listFiles { f -> f.isFile }
            ?.map { it.absolutePath }
            ?.toTypedArray()
            ?.takeIf { it.isNotEmpty() } ?: return
        MediaScannerConnection.scanFile(appContext, paths, null, null)
    }

    /** 진행 중인 다운로드를 취소한다. 실행 중이던 [run] 호출이 CANCELLED로 정리된다. */
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
