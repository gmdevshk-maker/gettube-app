package com.app.gettube.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.gettube.GetTubeApp
import com.app.gettube.data.DarkMode
import com.app.gettube.data.FileRepository
import com.app.gettube.data.SettingsRepository
import com.app.gettube.download.DownloadManager
import com.app.gettube.download.DownloadTask
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.DownloadFile
import com.app.gettube.model.MediaType
import com.app.gettube.model.SortOrder
import com.app.gettube.model.VideoQuality
import com.app.gettube.update.UpdateInfo
import com.app.gettube.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 앱 전체의 단일 화면 상태 홀더. Compose가 관찰할 상태와 인텐트 핸들러
 * (다운로드 / 취소 / 삭제 / 정렬 / 설정)를 노출한다.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as GetTubeApp
    private val settings = SettingsRepository(app)
    private val fileRepo = FileRepository()
    private val downloadManager: DownloadManager = appRef.downloadManager

    /** 진행 중/방금 실패한 다운로드. 목록 맨 위에 렌더링된다. */
    val tasks = downloadManager.tasks

    /** 네이티브 엔진 준비 여부(다운로드 버튼이 이 값에 따라 활성화). */
    val engineReady: StateFlow<Boolean> = appRef.engineReady

    /** 현재 설치된 yt-dlp 버전(설정 화면 표시용). */
    val engineVersion: StateFlow<String?> = appRef.engineVersion

    /** 앱(APK) 업데이트 상태. 업데이트 다이얼로그가 관찰한다. */
    val updateState: StateFlow<UpdateState> = appRef.appUpdater.state

    var url by mutableStateOf("")
        private set

    var sortOrder by mutableStateOf(settings.sortOrder)
        private set

    var files by mutableStateOf<List<DownloadFile>>(emptyList())
        private set

    var audioDownloadPath by mutableStateOf(settings.audioDownloadPath)
        private set

    var videoDownloadPath by mutableStateOf(settings.videoDownloadPath)
        private set

    var darkMode by mutableStateOf(settings.darkMode)
        private set

    var audioQuality by mutableStateOf(settings.audioQuality)
        private set

    var videoQuality by mutableStateOf(settings.videoQuality)
        private set

    /** UI가 한 번 소비하는 일회성 사용자 메시지(예: 입력 검증). */
    var transientMessage by mutableStateOf<String?>(null)

    init {
        refreshFiles()
        // 다운로드가 완료되면(백그라운드에서 끝난 경우 포함) 디스크 파일 목록을 갱신한다.
        viewModelScope.launch {
            downloadManager.fileListChanged.collect { refreshFiles() }
        }
    }

    fun onUrlChange(value: String) {
        url = value
    }

    fun onSortChange(order: SortOrder) {
        sortOrder = order
        settings.sortOrder = order
        refreshFiles()
    }

    /** 입력창에 있는 현재 URL로 오디오/영상 다운로드를 시작한다. */
    fun startDownload(type: MediaType, emptyUrlMessage: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            transientMessage = emptyUrlMessage
            return
        }
        url = ""
        launchDownload(target, type)
    }

    /** 실패한 작업을 목록에서 제거하고 같은 URL/유형으로 다시 다운로드한다. */
    fun retryDownload(task: DownloadTask) {
        downloadManager.dismiss(task.id)
        launchDownload(task.url, task.type)
    }

    private fun launchDownload(target: String, type: MediaType) {
        val destPath = if (type == MediaType.AUDIO) audioDownloadPath else videoDownloadPath
        // 실행은 앱 스코프의 DownloadManager가 맡는다(화면이 백그라운드로 가도 유지). 완료 후
        // 미디어 스캔/목록 갱신은 매니저가 처리하고 fileListChanged로 통지한다.
        downloadManager.enqueue(target, type, File(destPath), audioQuality, videoQuality)
    }

    /** 주어진 경로들을 MediaStore에 등록/갱신한다(외부 앱이 파일을 인식하도록). */
    private fun scanMedia(paths: Collection<String>?) {
        val arr = paths?.toTypedArray()?.takeIf { it.isNotEmpty() } ?: return
        MediaScannerConnection.scanFile(getApplication(), arr, null, null)
    }

    fun cancelDownload(id: String) = downloadManager.cancel(id)

    fun dismissTask(id: String) = downloadManager.dismiss(id)

    fun deleteFile(file: DownloadFile) {
        runCatching { File(file.path).delete() }
        // 삭제된 경로를 스캔하면 파일이 없으므로 MediaStore에서 항목이 제거된다(유령 항목 방지).
        scanMedia(listOf(file.path))
        refreshFiles()
    }

    /** 파일명을 변경한다(확장자는 유지). [newBaseName]은 확장자를 제외한 이름이다. */
    fun renameFile(file: DownloadFile, newBaseName: String) {
        // 파일명에 쓸 수 없는 문자 제거 + 공백 정리
        val clean = newBaseName.trim().replace(Regex("""[/\\:*?"<>|]"""), "")
        if (clean.isEmpty()) return
        val src = File(file.path)
        val ext = src.extension
        val newName = if (ext.isEmpty()) clean else "$clean.$ext"
        val dest = File(src.parentFile, newName)
        if (dest.absolutePath != src.absolutePath && !dest.exists()) {
            val moved = runCatching { src.renameTo(dest) }.getOrDefault(false)
            // 이전 경로는 제거되고 새 경로가 등록되도록 둘 다 스캔한다.
            if (moved) scanMedia(listOf(src.absolutePath, dest.absolutePath))
        }
        refreshFiles()
    }

    fun updateAudioPath(path: String) {
        val clean = path.trim()
        if (clean.isEmpty()) return
        audioDownloadPath = clean
        settings.audioDownloadPath = clean
        refreshFiles()
    }

    fun updateVideoPath(path: String) {
        val clean = path.trim()
        if (clean.isEmpty()) return
        videoDownloadPath = clean
        settings.videoDownloadPath = clean
        refreshFiles()
    }

    fun updateDarkMode(mode: DarkMode) {
        darkMode = mode
        settings.darkMode = mode
    }

    fun updateAudioQuality(quality: AudioQuality) {
        audioQuality = quality
        settings.audioQuality = quality
    }

    fun updateVideoQuality(quality: VideoQuality) {
        videoQuality = quality
        settings.videoQuality = quality
    }

    /** yt-dlp 엔진을 최신 nightly로 강제 재설치하고(최신이어도 다시 받음) 결과 메시지를 콜백으로 전달한다. */
    fun updateEngine(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(appRef.updateEngine(force = true))
        }
    }

    /** 사용자가 업데이트에 동의하면 APK를 받아 설치 화면을 띄운다. */
    fun startAppUpdate(info: UpdateInfo) {
        viewModelScope.launch { appRef.appUpdater.downloadAndInstall(info) }
    }

    /** 업데이트 안내/실패 다이얼로그를 닫는다(상태 초기화). */
    fun dismissAppUpdate() = appRef.appUpdater.dismiss()

    private var refreshJob: Job? = null

    fun refreshFiles() {
        // 연속 호출(경로/정렬 변경 등) 시 이전 스캔을 취소해 중복 I/O와 순서 꼬임을 막는다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // 디스크 I/O는 IO 디스패처에서, 상태 갱신은 메인에서 처리해 UI 끊김을 방지한다.
            val dirs = listOf(File(audioDownloadPath), File(videoDownloadPath))
            val list = withContext(Dispatchers.IO) { fileRepo.list(dirs, sortOrder) }
            files = list
        }
    }

    fun consumeMessage() {
        transientMessage = null
    }
}
