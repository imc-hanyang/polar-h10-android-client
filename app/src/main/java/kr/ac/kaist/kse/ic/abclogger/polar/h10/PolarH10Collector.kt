package kr.ac.kaist.kse.ic.abclogger.polar.h10

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import kr.ac.kaist.kse.ic.abclogger.polar.h10.data.MySQLiteLogger
import polar.com.sdk.api.PolarBleApiCallback
import polar.com.sdk.api.PolarBleApiDefaultImpl
import polar.com.sdk.api.errors.PolarInvalidArgument
import polar.com.sdk.api.model.PolarDeviceInfo
import polar.com.sdk.api.model.PolarHrData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PolarH10Collector(
    private val context: Context,
    private val onConnecting: () -> Unit,
    private val onConnect: () -> Unit,
    private val onDisconnect: () -> Unit,
    private val onEcgReady: () -> Unit
) : BaseCollector {
    companion object {
        private val SHARED_PREF_DEVICE_KEY = "kaist.iclab.abclogger.collector.polarh10.PolarH10Collector.DEVICE_KEY"

        class SharedPreferenceUtil(context: Context) {
            private val sharedPref: SharedPreferences = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE)

            fun getValue(key: String): String? = sharedPref.getString(key, null)

            fun putValue(key: String, value: String) {
                sharedPref.edit().putString(key, value).apply()
            }

            fun removeKey(key: String) {
                sharedPref.edit().remove(key).apply()
            }

            companion object {
                private var PRIVATE_MODE = 0
                private val PREF_NAME = "kaist.iclab.abclogger.SharedPreferenceUtil"

            }
        }
    }

    private val sharedPrefUtil = SharedPreferenceUtil(context)

    private val logger = MySQLiteLogger(context)
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    fun log(type: String, timestamp: Long, msg: String) {
        executorService.submit { logger.data(type, timestamp, msg) }
    }

    private fun getDeviceId(): String? = sharedPrefUtil.getValue(SHARED_PREF_DEVICE_KEY)

    private fun storeDeviceId(deviceId: String) {
        sharedPrefUtil.putValue(SHARED_PREF_DEVICE_KEY, deviceId)
    }

    private fun removeDeviceId() {
        sharedPrefUtil.removeKey(SHARED_PREF_DEVICE_KEY)
    }

    private val api = PolarBleApiDefaultImpl.defaultImplementation(context, 15).apply {
        setPolarFilter(false)
        setAutomaticReconnection(true)

        setApiCallback(object : PolarBleApiCallback() {
            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                log("POLAR CONNECTION", System.currentTimeMillis(), "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                log("POLAR CONNECTION", System.currentTimeMillis(), "CONNECTED: ${polarDeviceInfo.deviceId}")
                onConnect.invoke()
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                log("POLAR BATTERY", System.currentTimeMillis(), "level: $level ")
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData) {
                log("POLAR RR", System.currentTimeMillis(), "value: ${data.hr}, rrsMs: ${data.rrsMs}, rr: ${data.rrs}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                log("POLAR CONNECTION", System.currentTimeMillis(), "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                onDisconnect.invoke()
            }

            override fun ecgFeatureReady(identifier: String) {
                val res = requestEcgSettings(identifier).toFlowable()
                    .flatMap { startEcgStreaming(identifier, it.maxSettings()) }
                    .subscribe(
                        { log("POLAR ECG", System.currentTimeMillis(), "${it.timeStamp}, values(yV): ${it.samples}") },
                        {
                            log("POLAR ECG", System.currentTimeMillis(), "Error MSG: $it")

                        },
                        {
                            log("POLAR ECG", System.currentTimeMillis(), "complete")

                        }
                    )
                onEcgReady.invoke()
            }
        })
    }

    override fun start() {
        try {
            api.connectToDevice(getDeviceId()!!)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            removeDeviceId()
            polarInvalidArgument.printStackTrace()
        }
    }

    /**
     * After calling this method - stop(), device ID is no longer stored.
     */
    override fun stop() {
        try {
            api.disconnectFromDevice(getDeviceId()!!)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            polarInvalidArgument.printStackTrace()
        }
        //removeDeviceId()
    }

    /**
     * Assuming that permissions are already obtained before calling this method - checkAvailability()
     */
    override fun checkAvailability(): Boolean = (!getDeviceId().isNullOrBlank())

    override fun getRequiredPermissions(): List<String> = listOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun newIntentForSetup(): Intent? {
        val list = listOf("4373B624", "43732225", "4EBE4924", "75052E29", "5AF6E02C")
        val dataAdapter = ArrayAdapter<String>(
            context,
            android.R.layout.simple_spinner_item,
            list
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val spinner = Spinner(context).apply { adapter = dataAdapter }
        spinner.setPadding(20, 40, 20, 20)

        /*val deviceIdEditText = EditText(context)
        deviceIdEditText.setText("4373B624")*/

        AlertDialog.Builder(context)
            .setTitle("Please select Polar H10 device ID")
            .setView(spinner)
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ -> storeDeviceId(spinner.selectedItem as String) }
            .create().show()

        return null
    }
}
