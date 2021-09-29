package kr.ac.kaist.kse.ic.abclogger.polar.h10

import android.content.Intent

interface BaseCollector {
    fun start()
    fun stop()
    fun checkAvailability(): Boolean
    fun getRequiredPermissions(): List<String>
    fun newIntentForSetup(): Intent?
}
