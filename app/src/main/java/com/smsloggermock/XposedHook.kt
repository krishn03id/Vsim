package com.smsloggermock

import android.app.Activity
import android.app.PendingIntent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.ArrayList

class XposedHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.smslogger") return

        XposedBridge.log("VSim: Initializing in ${lpparam.packageName}")

        // 1. Hook SubscriptionManager.getActiveSubscriptionInfoList
        // Using a simpler approach for non-root stability
        try {
            XposedHelpers.findAndHookMethod(
                SubscriptionManager::class.java.name,
                lpparam.classLoader,
                "getActiveSubscriptionInfoList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = ArrayList<SubscriptionInfo>()
                        val jioSim = createFakeSubscriptionInfo(4, 1, "Jio", "JIO 4G | Jio", "", "in", "405", "856")
                        if (jioSim != null) {
                            list.add(jioSim)
                            param.result = list
                        }
                    }
                }
            )
        } catch (e: Exception) {}

        // 2. Hook TelephonyManager for basic SIM ready state
        try {
            XposedHelpers.findAndHookMethod(
                TelephonyManager::class.java.name,
                lpparam.classLoader,
                "getSimState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = TelephonyManager.SIM_STATE_READY
                    }
                }
            )
        } catch (e: Exception) {}

        // 3. Hook SmsManager.sendTextMessage
        try {
            XposedHelpers.findAndHookMethod(
                SmsManager::class.java.name,
                lpparam.classLoader,
                "sendTextMessage",
                String::class.java,
                String::class.java,
                String::class.java,
                PendingIntent::class.java,
                PendingIntent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sentIntent = param.args[3] as? PendingIntent
                        val deliveryIntent = param.args[4] as? PendingIntent
                        
                        param.result = null // Block actual send
                        
                        // Use main looper handler for non-root stability
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        handler.postDelayed({
                            try { sentIntent?.send(Activity.RESULT_OK) } catch (e: Exception) {}
                        }, 800)
                        
                        handler.postDelayed({
                            try { deliveryIntent?.send(Activity.RESULT_OK) } catch (e: Exception) {}
                        }, 1600)
                    }
                }
            )
        } catch (e: Exception) {}
    }

    private fun createFakeSubscriptionInfo(id: Int, slotIndex: Int, display: String, carrier: String, number: String, iso: String, mcc: String, mnc: String): SubscriptionInfo? {
        try {
            // Safer way to create SubscriptionInfo without Unsafe for non-root
            val fakeInfo = XposedHelpers.newInstance(SubscriptionInfo::class.java) as SubscriptionInfo

            XposedHelpers.setIntField(fakeInfo, "mId", id)
            XposedHelpers.setIntField(fakeInfo, "mSimSlotIndex", slotIndex)
            XposedHelpers.setObjectField(fakeInfo, "mDisplayName", display)
            XposedHelpers.setObjectField(fakeInfo, "mCarrierName", carrier)
            XposedHelpers.setObjectField(fakeInfo, "mNumber", number)
            XposedHelpers.setObjectField(fakeInfo, "mCountryIso", iso)
            
            try { XposedHelpers.setObjectField(fakeInfo, "mMcc", mcc) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mMnc", mnc) } catch(e:Exception){}
            try { XposedHelpers.setIntField(fakeInfo, "mMcc", mcc.toInt()) } catch(e:Exception){}
            try { XposedHelpers.setIntField(fakeInfo, "mMnc", mnc.toInt()) } catch(e:Exception){}

            return fakeInfo
        } catch (e: Exception) {
            return null
        }
    }
}
