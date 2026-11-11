package com.kassa.ghesta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.Sync
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.GhestaRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GhestaRoot() }
    }

    override fun onResume() {
        super.onResume()
        Store.onResume()
        // Re-arm the daily reminder every time the app is opened (covers aggressive battery managers).
        Reminders.schedule(this)
        Sync.requestSync(0)
    }
}
