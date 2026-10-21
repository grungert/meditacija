package com.grungert.nebo.grass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/** Asks Android's own speech recognizers which languages they support, on the device and online, and logs it. */
class SpeechProbe : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "recognition available=${SpeechRecognizer.isRecognitionAvailable(this)} onDevice=${SpeechRecognizer.isOnDeviceRecognitionAvailable(this)}")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val cb = { kind: String -> object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                Log.i(TAG, "$kind installed=${s.installedOnDeviceLanguages}")
                Log.i(TAG, "$kind pending=${s.pendingOnDeviceLanguages}")
                Log.i(TAG, "$kind downloadable=${s.supportedOnDeviceLanguages}")
                Log.i(TAG, "$kind online=${s.onlineLanguages}")
            }
            override fun onError(error: Int) { Log.i(TAG, "$kind error=$error") }
        } }
        runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(this).checkRecognitionSupport(intent, mainExecutor, cb("onDevice")) }
            .onFailure { Log.i(TAG, "onDevice failed $it") }
        runCatching { SpeechRecognizer.createSpeechRecognizer(this).checkRecognitionSupport(intent, mainExecutor, cb("default")) }
            .onFailure { Log.i(TAG, "default failed $it") }
    }
    companion object { const val TAG = "SpeechProbe" }
}
