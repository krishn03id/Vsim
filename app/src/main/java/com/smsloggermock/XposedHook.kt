package com.smsloggermock

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
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
        // Activate UI status
        if (lpparam.packageName == "com.smsloggermock") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.smsloggermock.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            } catch (e: Exception) {
                XposedBridge.log("VSim: Error hooking UI status - ${e.message}")
            }
            return
        }

        // Skip System UI and Android Framework to avoid bootloops
        if (lpparam.packageName == "android" || lpparam.packageName == "com.android.systemui") return

        hookSubscriptionManager(lpparam)
        hookTelephonyManager(lpparam)
        hookSmsManager(lpparam)
    }

    private fun hookSubscriptionManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val smClass = XposedHelpers.findClass(SubscriptionManager::class.java.name, lpparam.classLoader)
            for (method in smClass.declaredMethods) {
                val name = method.name
                
                if (name == "getActiveSubscriptionInfoList" || name == "getAvailableSubscriptionInfoList") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val list = ArrayList<SubscriptionInfo>()
                                val jioSim = createFakeSubscriptionInfo(4, 1, "Jio", "JIO 4G | Jio", "", "in", "405", "856")
                                if (jioSim != null) {
                                    list.add(jioSim)
                                    param.result = list
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("VSim Error in $name: ${e.message}")
                            }
                        }
                    })
                } else if (name == "getActiveSubscriptionInfoCount" || name == "getActiveSubscriptionInfoCountMax" || name == "getAvailableSubscriptionInfoCountMax") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = 1
                        }
                    })
                } else if (name == "getActiveSubscriptionInfo" || name == "getActiveSubscriptionInfoForSimSlotIndex") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val jioSim = createFakeSubscriptionInfo(4, 1, "Jio", "JIO 4G | Jio", "", "in", "405", "856")
                                if (jioSim != null) {
                                    param.result = jioSim
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("VSim Error in $name: ${e.message}")
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("VSim Error hooking SubscriptionManager: ${e.message}")
        }
    }

    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tmClass = XposedHelpers.findClass(TelephonyManager::class.java.name, lpparam.classLoader)
            for (method in tmClass.declaredMethods) {
                val name = method.name
                if (name == "getSimState") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = TelephonyManager.SIM_STATE_READY
                        }
                    })
                } else if (name == "hasIccCard") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    })
                } else if (name == "getNetworkOperatorName" || name == "getSimOperatorName") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = "Jio"
                        }
                    })
                } else if (name == "getNetworkOperator" || name == "getSimOperator") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = "405856"
                        }
                    })
                } else if (name == "getSimCountryIso" || name == "getNetworkCountryIso") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = "in"
                        }
                    })
                } else if (name == "getLine1Number") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = ""
                        }
                    })
                }
            }
        } catch (e: Exception) {
             XposedBridge.log("VSim Error hooking TelephonyManager: ${e.message}")
        }
    }

    private fun hookSmsManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val smsClass = XposedHelpers.findClass(SmsManager::class.java.name, lpparam.classLoader)
            
            for (method in smsClass.declaredMethods) {
                val methodName = method.name
                
                // Intercept ALL forms of SMS sending natively, capturing any new Android methods
                if (methodName.startsWith("send") && methodName.contains("Message")) {
                    
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                XposedBridge.log("VSim: Intercepted ${methodName} in ${lpparam.packageName}")
                                
                                // Neutralize actual sending to prevent hardware exception
                                param.result = null 
                                
                                val paramTypes = method.parameterTypes
                                val args = param.args
                                
                                var sentIntentFound = false
                                var deliveryIntentFound = false

                                // Deep scan the method arguments to find any PendingIntents
                                // This guarantees compatibility across Android 9 to Android 15
                                for (i in paramTypes.indices) {
                                    val type = paramTypes[i]
                                    val arg = args[i]
                                    
                                    if (arg == null) continue

                                    if (type == PendingIntent::class.java) {
                                        if (!sentIntentFound) {
                                            triggerPendingIntent(arg as PendingIntent, 600)
                                            sentIntentFound = true
                                        } else if (!deliveryIntentFound) {
                                            triggerPendingIntent(arg as PendingIntent, 1800)
                                            deliveryIntentFound = true
                                        }
                                    } else if (type == ArrayList::class.java || type == List::class.java) {
                                        try {
                                            val list = arg as? List<*>
                                            if (list != null && list.isNotEmpty() && list[0] is PendingIntent) {
                                                if (!sentIntentFound) {
                                                    list.forEach { intent -> triggerPendingIntent(intent as PendingIntent, 650) }
                                                    sentIntentFound = true
                                                } else if (!deliveryIntentFound) {
                                                    list.forEach { intent -> triggerPendingIntent(intent as PendingIntent, 1850) }
                                                    deliveryIntentFound = true
                                                }
                                            }
                                        } catch(e: Exception){
                                            XposedBridge.log("VSim List Parsing Error: ${e.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("VSim Generic Hook Error in $methodName: ${e.message}")
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("VSim Error hooking SmsManager: ${e.message}")
        }
    }
    
    private fun triggerPendingIntent(intent: PendingIntent, delayMs: Long) {
        Thread {
            try {
                Thread.sleep(delayMs)
                intent.send(Activity.RESULT_OK)
                XposedBridge.log("VSim: Successfully dispatched PendingIntent callback")
            } catch (e: Exception) {
                XposedBridge.log("VSim: Error dispatching PendingIntent - ${e.message}")
            }
        }.start()
    }

    private fun createFakeSubscriptionInfo(id: Int, slotIndex: Int, display: String, carrier: String, number: String, iso: String, mcc: String, mnc: String): SubscriptionInfo? {
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null)
            val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)

            val fakeInfo = allocateInstanceMethod.invoke(unsafe, SubscriptionInfo::class.java) as SubscriptionInfo

            try { XposedHelpers.setIntField(fakeInfo, "mId", id) } catch(e:Exception){}
            try { XposedHelpers.setIntField(fakeInfo, "mSimSlotIndex", slotIndex) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mDisplayName", display) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mCarrierName", carrier) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mNumber", number) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mCountryIso", iso) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mMcc", mcc) } catch(e:Exception){}
            try { XposedHelpers.setObjectField(fakeInfo, "mMnc", mnc) } catch(e:Exception){}
            
            // Fallback for older Android versions where MMC/MNC are Ints instead of Strings
            try { XposedHelpers.setIntField(fakeInfo, "mMcc", mcc.toInt()) } catch(e:Exception){}
            try { XposedHelpers.setIntField(fakeInfo, "mMnc", mnc.toInt()) } catch(e:Exception){}

            return fakeInfo
        } catch (e: Exception) {
            XposedBridge.log("VSim Error creating SubscriptionInfo: ${e.message}")
            return null
        }
    }
}
