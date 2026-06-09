package com.app.gettube.data

import com.app.gettube.model.DownloadFile
import com.app.gettube.model.MediaType
import com.app.gettube.model.SortOrder
import java.io.File

/** 다운로드 폴더를 읽어 완료된 파일의 정렬된 목록을 만든다. */
class FileRepository {

    private val audioExtensions = setOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac")

    /** 탐색기에 절대 보이면 안 되는, 다운로드 중 생성되는 yt-dlp 임시 파일들. */
    private val tempExtensions = setOf("part", "ytdl", "tmp", "temp")

    /** 여러 다운로드 폴더(음악/영상)를 한꺼번에 읽어 합치고 정렬한다. 같은 경로는 한 번만 본다. */
    fun list(dirs: Collection<File>, order: SortOrder): List<DownloadFile> {
        val seenDirs = HashSet<String>()
        val mapped = dirs
            .filter { seenDirs.add(it.absolutePath) }
            .flatMap { dir ->
                dir.listFiles { f -> f.isFile && f.extension.lowercase() !in tempExtensions }
                    ?.toList()
                    ?: emptyList()
            }
            .map { f ->
                val ext = f.extension.lowercase()
                DownloadFile(
                    path = f.absolutePath,
                    name = f.name,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                    type = if (ext in audioExtensions) MediaType.AUDIO else MediaType.VIDEO,
                )
            }

        return when (order) {
            SortOrder.NAME -> mapped.sortedBy { it.name.lowercase() }
            SortOrder.DATE -> mapped.sortedByDescending { it.lastModified }
            SortOrder.SIZE -> mapped.sortedByDescending { it.sizeBytes }
        }
    }
}
