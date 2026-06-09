package com.app.gettube.ui

import android.app.Application
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
import com.app.gettube.model.AudioQuality
import com.app.gettube.model.DownloadFile
import com.app.gettube.model.MediaType
import com.app.gettube.model.SortOrder
import com.app.gettube.model.VideoQuality
import kotlinx.coroutines.Dispatchers
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

    var url by mutableStateOf("")
        private set

    var sortOrder by mutableStateOf(SortOrder.NAME)
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
    }

    fun onUrlChange(value: String) {
        url = value
    }

    fun onSortChange(order: SortOrder) {
        sortOrder = order
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
        val destPath = if (type == MediaType.AUDIO) audioDownloadPath else videoDownloadPath
        viewModelScope.launch {
            downloadManager.download(target, type, File(destPath), audioQuality, videoQuality)
            refreshFiles()
        }
    }

    fun cancelDownload(id: String) = downloadManager.cancel(id)

    fun dismissTask(id: String) = downloadManager.dismiss(id)

    fun deleteFile(file: DownloadFile) {
        runCatching { File(file.path).delete() }
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
            runCatching { src.renameTo(dest) }
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

    /** yt-dlp 엔진을 최신 nightly로 강제 업데이트하고 결과 메시지를 콜백으로 전달한다. */
    fun updateEngine(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(appRef.updateEngine())
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
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
