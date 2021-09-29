package kr.ac.kaist.kse.ic.abclogger.polar.h10

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kr.ac.kaist.kse.ic.abclogger.polar.h10.data.MySQLiteLogger
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {
    private lateinit var polarH10: PolarH10Collector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        polarH10 = PolarH10Collector(
            context = this,
            onConnecting = { tvLog.append("[${nowTs()}] Connecting...\n") },
            onConnect = { tvLog.append("[${nowTs()}] Connected\n") },
            onDisconnect = { tvLog.append("[${nowTs()}] Disconnected\n") },
            onEcgReady = { tvLog.append("[${nowTs()}] ECG ready\n") }
        )

        buttonStart.setOnClickListener { polarH10.start() }
        buttonStop.setOnClickListener { polarH10.stop() }
        buttonExport.setOnClickListener {
            MySQLiteLogger.exportSQLite(
                this,
                "010-9968-8196"
            )
            tvLog.append("[${nowTs()}] Data exported\n")
        }

        if (!polarH10.getRequiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED })
            this.requestPermissions(polarH10.getRequiredPermissions().toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()

        if (polarH10.getRequiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED })
            if (!polarH10.checkAvailability())
                polarH10.newIntentForSetup()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 1)
            tvLog.append("[${nowTs()}] BLE ready\n")
    }

    private fun nowTs(): String {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now())
    }
}
