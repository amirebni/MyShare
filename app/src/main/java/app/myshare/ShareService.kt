package app.myshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import java.security.SecureRandom

class ShareService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var token = ""
        @Volatile var error: String? = null
        const val PORT = 8080
    }

    private var server: FileServer? = null
    private var wl: PowerManager.WakeLock? = null
    private var wf: WifiManager.WifiLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) {
            error = null
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val sr = SecureRandom()
            token = (1..12).map { chars[sr.nextInt(chars.length)] }.joinToString("")
            goForeground()
            try {
                val s = FileServer(Environment.getExternalStorageDirectory(), token, PORT)
                s.start(60000, false)
                server = s
            } catch (e: Exception) {
                error = (e.message ?: e.javaClass.simpleName) + " (پورت " + PORT + " اشغال است؟)"
                stopSelf()
                return START_NOT_STICKY
            }
            wl = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myshare:wl").also { it.acquire() }
            wf = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "myshare:wf").also { it.acquire() }
            running = true
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("share", "MyShare", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ShareService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "share")
            .setContentTitle("MyShare فعال است")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .addAction(0, "توقف", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        running = false
        token = ""
        wl?.let { if (it.isHeld) it.release() }
        wf?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
