package com.app.gettube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.app.gettube.GetTubeApp
import com.app.gettube.MainActivity
import com.app.gettube.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 다운로드가 진행되는 동안만 떠 있는 포그라운드 서비스. 존재 목적은 두 가지다.
 *  1. OS가 메모리 확보를 위해 앱 프로세스를 종료하지 못하게 막는다(백그라운드 다운로드 유지).
 *  2. Partial WakeLock으로 화면이 꺼져도 CPU가 멈추지 않게 한다(Doze 중 중단 방지).
 *
 * 실제 다운로드는 [DownloadManager]가 앱 스코프에서 수행하며, 이 서비스는 그 [DownloadManager.tasks]를
 * 관찰해 진행 알림을 갱신하고, 활성 다운로드가 사라지면 스스로 종료한다.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as GetTubeApp).downloadManager
        val active = manager.tasks.value.filter { it.isActive }

        // startForegroundService 이후 5초 내에 반드시 startForeground를 호출해야 하므로 즉시 승격한다.
        startForeground(buildNotification(active))
        acquireWakeLock()

        if (active.isEmpty()) {
            // 시작하자마자 할 일이 없으면(예: 다운로드가 즉시 실패) 정리하고 종료한다.
            stopSelfSafely()
        } else if (!observing) {
            observing = true
            observeTasks(manager)
        }
        // 프로세스가 강제 종료되면 네이티브 다운로드도 함께 죽어 이어받을 수 없으므로 재생성하지 않는다.
        return START_NOT_STICKY
    }

    private fun observeTasks(manager: DownloadManager) {
        manager.tasks
            .onEach { tasks ->
                val active = tasks.filter { it.isActive }
                if (active.isEmpty()) {
                    stopSelfSafely()
                } else {
                    // 진행 중에는 알림만 갱신한다(권한이 없으면 no-op이며 서비스는 계속 유지된다).
                    NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(active))
                }
            }
            .launchIn(scope)
    }

    private fun buildNotification(active: List<DownloadTask>): android.app.Notification {
        val downloading = active.filter { it.state == DownloadState.DOWNLOADING }
        val text = when {
            active.isEmpty() -> getString(R.string.notif_preparing)
            active.size == 1 -> active.first().title.ifBlank { getString(R.string.notif_preparing) }
            else -> getString(R.string.notif_downloading_many, active.size)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .setSilent(true)

        // 진행률: 다운로드 중인 작업이 있으면 첫 작업 기준 퍼센트, 아직 정보 조회 중이면 불확정 바.
        if (downloading.isNotEmpty()) {
            val percent = (downloading.first().progress * 100).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
        } else if (active.isNotEmpty()) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun startForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            // 안전 상한(정상 흐름에서는 서비스 종료 시 해제된다). 매우 긴 다운로드도 커버.
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopSelfSafely() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW, // 소리/헤드업 없이 상태만 표시
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
        private const val WAKELOCK_TAG = "GetTube:download"
        private const val WAKELOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L // 3시간 안전 상한

        /** 다운로드 시작 시 호출. 이미 떠 있으면 재사용되고, onStartCommand가 다시 실행된다. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java),
            )
        }
    }
}
