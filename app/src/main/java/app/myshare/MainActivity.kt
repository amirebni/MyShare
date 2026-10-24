package app.myshare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var btn: Button
    private lateinit var info: TextView
    private lateinit var qr: ImageView
    private var urls: List<String> = emptyList()
    private var idx = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.setPadding(48, 96, 48, 48)
        btn = Button(this)
        info = TextView(this)
        info.textSize = 16f
        info.setTextIsSelectable(true)
        qr = ImageView(this)
        l.addView(btn)
        l.addView(info)
        l.addView(qr, LinearLayout.LayoutParams(700, 700))
        val sv = ScrollView(this)
        sv.addView(l)
        setContentView(sv)
        btn.setOnClickListener { toggle() }
        qr.setOnClickListener {
            if (urls.size > 1) {
                idx = (idx + 1) % urls.size
                draw()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun toggle() {
        if (ShareService.running) {
            stopService(Intent(this, ShareService::class.java))
            ShareService.running = false
            refresh()
            return
        }
        if (!Environment.isExternalStorageManager()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
            Toast.makeText(this, "دسترسی «همه فایل‌ها» را بده و برگرد", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        idx = 0
        startForegroundService(Intent(this, ShareService::class.java))
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 700)
    }

    private fun refresh() {
        val on = ShareService.running
        btn.text = if (on) "توقف اشتراک" else "شروع اشتراک"
        if (!on) {
            urls = emptyList()
            val e = ShareService.error
            info.text = if (e != null) "خطا: " + e else "خاموش است"
            qr.setImageBitmap(null)
            return
        }
        urls = ips().map { "http://" + it + ":" + ShareService.PORT + "/?t=" + ShareService.token }
        draw()
    }

    private fun draw() {
        if (urls.isEmpty()) {
            info.text = "به وای‌فای وصل شو یا هات‌اسپات را روشن کن، بعد دوباره باز کن"
            qr.setImageBitmap(null)
            return
        }
        if (idx >= urls.size) idx = 0
        var t = "هشدار: دسترسی کامل به همه فایل‌ها باز است. فقط روی شبکه مطمئن (خانه یا هات‌اسپات خودت) استفاده کن.\n\n"
        t += "آدرس را در مرورگر دستگاه دیگر (روی همین شبکه) باز کن یا QR را اسکن کن:\n\n"
        t += urls.joinToString("\n\n")
        if (urls.size > 1) t += "\n\nQR مربوط به آدرس " + (idx + 1) + " از " + urls.size + " است؛ برای تغییر روی QR بزن."
        info.text = t
        qr.setImageBitmap(qrBitmap(urls[idx]))
    }

    private fun ips(): List<String> {
        val ok = listOf("wlan", "ap", "swlan", "eth", "rndis", "p2p", "bt-pan")
        val res = ArrayList<Pair<String, String>>()
        val en = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (ni in en) {
            if (!ni.isUp || ni.isLoopback || ok.none { ni.name.startsWith(it) }) continue
            for (a in ni.inetAddresses) {
                if (a is Inet4Address && a.isSiteLocalAddress) res.add(Pair(ni.name, a.hostAddress ?: continue))
            }
        }
        return res.sortedBy { if (it.first.startsWith("wlan")) 0 else 1 }.map { it.second }
    }

    private fun qrBitmap(text: String): Bitmap {
        val size = 600
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }
}
