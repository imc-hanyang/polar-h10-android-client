package kr.ac.kaist.kse.ic.abclogger.polar.h10.data

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import kaist.iclab.abclogger.data.DatabaseHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MySQLiteLogger(base: Context) : ContextWrapper(base) {
    init {
        debug(TAG,"MySQLiteLogger()")
    }

    private fun addContentValues(TAG: String, msg: String, timestamp: Long) {
        val contentValues = ContentValues()
        contentValues.put(MySQLiteOpenHelper.LOG_FIELD_TYPE, TAG)
        contentValues.put(MySQLiteOpenHelper.LOG_FIELD_JSON, msg)
        contentValues.put(MySQLiteOpenHelper.LOG_FIELD_REG, timestamp)
        getContentValuesListToWrite().add(contentValues)
        tryToSend(this, false)
    }

    fun debug(TAG: String, msg: String) {
        addContentValues(TAG, msg, System.currentTimeMillis());
        Log.d(TAG, msg)
    }

    fun info(TAG: String, msg: String) {
        addContentValues(TAG, msg, System.currentTimeMillis());
        Log.i(TAG, msg)
    }

    fun data(tag: String, timestamp: Long, msg: String) {
        addContentValues(tag, msg, timestamp)
        Log.d(tag, "time: ${SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(timestamp)}, msg: $msg")
    }

    companion object {

        private val TAG = MySQLiteLogger::class.java.simpleName

        private val contentValuesListToWrite = ArrayList<ContentValues>()

        private val MAX_NUMBER_OF_LOGS_IN_MEMORY = 900

        private var sending = false

        private var exported = false

        fun setSending(flag: Boolean) { sending = flag }

        fun setExported(flag: Boolean) {exported = flag}

        fun getExported(): Boolean { return exported}

        private fun getContentValuesListToWrite(): MutableList<ContentValues> {
            return contentValuesListToWrite
        }

        private fun forceToWriteContentValues(context: Context): Boolean{
            return tryToSend(context, true)
        }

        private fun tryToSend(context: Context, force: Boolean): Boolean {
            //Log.d(TAG, "tryToSend // force: $force, sending: $sending, exported: $exported")
            return if (sending) {
                false
            } else if (getContentValuesListToWrite().size > MAX_NUMBER_OF_LOGS_IN_MEMORY || force) {
                setSending(true)
                val contentValuesArray = getContentValuesListToWrite().toTypedArray()
                Log.d(TAG, "tryToSend () BulkInsert start")
                val mHandler = Handler(Looper.getMainLooper())
                mHandler.postDelayed({
                    val handler = DatabaseHandler(context.contentResolver)
                    handler.startBulkInsert(1, -1, DataProvider.CONTENT_URI_LOG, contentValuesArray)
                }, 0)
                getContentValuesListToWrite().clear()
                Log.d(TAG, "tryToSend () BulkInsert end")
                true
            } else {
                false
            }
        }

        fun exportSQLite(context: Context, phoneNumber: String) {
            Log.d(TAG, "sqlite export start")
            forceToWriteContentValues(context)
            val mHandler = Handler(Looper.getMainLooper())
            mHandler.postDelayed({
                exportSQLiteFile()
            }, 10 * 1000)
        }

        private fun exportSQLiteFile() {
            try {
                val internal = Environment.getDataDirectory()
                val external = Environment.getExternalStorageDirectory()

                val directory = File(external.absolutePath + "/ABC_Logger")
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                if (external.canWrite()) {
                    val name = MySQLiteOpenHelper.DATABASE_NAME
                    var pNum = "010-9968-8196"
                    val currentDB = File(internal, "/user/0/kr.ac.kaist.kse.ic.abclogger.polar.h10/databases/$name")
                    val exportDB = File(external, "/ABC_Logger/${SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Date(System.currentTimeMillis()))}-$pNum.db")

                    if (currentDB.exists()) {
                        val src = FileInputStream(currentDB).channel
                        val dst = FileOutputStream(exportDB).channel

                        dst.transferFrom(src, 0, src.size())

                        src.close()
                        dst.close()

                        Log.d("Ria", ">>> SQLite > SQLiteExport")
                        setExported(true)
                    } else {
                        Log.d(TAG, "current db not found.")
                    }
                    //return currentDB
                } else {
                    Log.d(TAG, "external write permission denied.")
                }
            } catch (e: Exception) {
                Log.d(TAG, "DB export failed.")
            }
        }
    }
}