package com.app.gettube.model

/** 다운로드/파일이 오디오(MP3)인지 영상(MP4/MKV)인지 구분. */
enum class MediaType { AUDIO, VIDEO }

/** 다운로드 파일 탐색기의 정렬 옵션. */
enum class SortOrder(val labelRes: Int) {
    NAME(com.app.gettube.R.string.sort_name),
    DATE(com.app.gettube.R.string.sort_date),
    SIZE(com.app.gettube.R.string.sort_size),
}

/** 음악(오디오) 다운로드 품질. */
enum class AudioQuality(val labelRes: Int) {
    MP3_192(com.app.gettube.R.string.quality_audio_192),   // 192kbps MP3
    ORIGINAL(com.app.gettube.R.string.quality_audio_original), // 원본 코덱 무손실
}

/** 영상(비디오) 다운로드 품질(해상도 상한). */
enum class VideoQuality(val labelRes: Int) {
    P1080(com.app.gettube.R.string.quality_video_1080),    // 1080p 이하
    P720(com.app.gettube.R.string.quality_video_720),      // 720p 이하
    ORIGINAL(com.app.gettube.R.string.quality_video_original), // 해상도 제한 없음(최고)
}

/** 다운로드 폴더 안 디스크에 존재하는 완료된 파일. */
data class DownloadFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: MediaType,
)
