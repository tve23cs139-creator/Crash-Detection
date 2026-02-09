package com.example.crashdetection

import android.telephony.SmsManager

object SmsHelper {
    fun send(number: String, text: String) {
        SmsManager.getDefault()
            .sendTextMessage(number, null, text, null, null)
    }
}