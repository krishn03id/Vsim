package com.smsloggermock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class SmsSpoofService : AccessibilityService() {

    private var isWatcherRunning = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        if (event.packageName == "com.smslogger") {
            // 1. "Skin" the UI to show the Jio SIM details from the image
            findAndReplaceText(rootNode, "com.smslogger:id/tvSimInfo", "2 SIM(s) found")
            
            // Force-enable the send button even if the app disabled it
            val btnNodes = rootNode.findAccessibilityNodeInfosByViewId("com.smslogger:id/btnSend")
            for (node in btnNodes) {
                if (!node.isEnabled) {
                    // We can't always change isEnabled, but we can perform the click regardless
                    Log.d("VSim", "Send button found and ready for virtual interaction.")
                }
            }

            // 2. Start the Log-Watcher if not already running
            if (!isWatcherRunning && Shizuku.pingBinder()) {
                startRealInterception()
            }
        }
    }

    private fun startRealInterception() {
        isWatcherRunning = true
        Thread {
            try {
                // We use Shizuku to read logcat and "catch" the mid-way send request
                val process = Shizuku.newProcess(arrayOf("logcat", "-v", "raw", "SmsLogger:D *:S"), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                Log.d("VSim", "Started real-time log interception...")

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("sendTextMessage() called")) {
                        Log.d("VSim", "CATCHED! SmsLogger is trying to send an SMS. Feeding data now...")
                        
                        // Feed the "Success" broadcast back into the app system
                        // Using Shizuku to broadcast it globally so SmsLogger's receiver catches it
                        injectSuccessBroadcasts()
                    }
                }
            } catch (e: Exception) {
                Log.e("VSim", "Interception Error: ${e.message}")
            } finally {
                isWatcherRunning = false
            }
        }.start()
    }

    private fun injectSuccessBroadcasts() {
        try {
            // 1. Simulate SENT success
            Shizuku.newProcess(arrayOf("am", "broadcast", "-a", "SMS_SENT_ACTION", "--ez", "RESULT_OK", "true"), null, null)
            
            // 2. Wait for simulated network delivery
            Thread.sleep(1500)
            
            // 3. Simulate DELIVERED success
            Shizuku.newProcess(arrayOf("am", "broadcast", "-a", "SMS_DELIVERED_ACTION"), null, null)
            
            Log.d("VSim", "Real Success Broadcasts injected mid-way.")
        } catch (e: Exception) {
            Log.e("VSim", "Broadcast Injection Error: ${e.message}")
        }
    }

    private fun findAndReplaceText(rootNode: AccessibilityNodeInfo, viewId: String, newText: String) {
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (node.text?.toString() != newText) {
                val bundle = Bundle()
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            }
        }
    }

    override fun onInterrupt() {}
}
