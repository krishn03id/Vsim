package com.smsloggermock

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        if (isModuleActive()) {
            tvStatus.text = "✅ Active and Running"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvStatus.text = "❌ Module Disabled"
            tvStatus.setTextColor(Color.parseColor("#F44336"))
        }
    }

    // This method will be hooked by the Xposed module to return true
    private fun isModuleActive(): Boolean {
        return false
    }
}
