package kr.ac.kaist.kse.ic.abclogger.polar.h10

import android.content.pm.PackageManager
import android.os.AsyncTask
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
                onConnecting = { runOnUiThread { tvLog.text = "${tvLog.text}\n[${nowTs()}] Connecting..." } },
                onConnect = { runOnUiThread { tvLog.text = "${tvLog.text}\n[${nowTs()}] ECG connected" } },
                onDisconnect = { runOnUiThread { tvLog.text = "${tvLog.text}\n[${nowTs()}] ECG disconnected..." } },
                onEcgReady = { runOnUiThread { tvLog.text = "${tvLog.text}\n[${nowTs()}] ECG is now collecting data" } }
        )

        buttonStart.setOnClickListener {
            tvLog.text = "${tvLog.text}\n[${nowTs()}] Starting..."
            polarH10.start()
        }
        buttonStop.setOnClickListener {
            tvLog.text = "${tvLog.text}\n[${nowTs()}] Stopping..."
            polarH10.stop()
        }
        buttonExport.setOnClickListener {
            tvLog.text = "${tvLog.text}\n[${nowTs()}] Exporting..."
            MySQLiteLogger.exportSQLite(
                    this,
                    "010-9968-8196"
            )
            tvLog.text = "${tvLog.text}\n[${nowTs()}] Data exported"
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
