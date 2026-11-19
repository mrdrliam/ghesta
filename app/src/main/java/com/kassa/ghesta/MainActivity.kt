package com.kassa.ghesta

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.Sync
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.AppLock
import com.kassa.ghesta.ui.GhestaRoot
import com.kassa.ghesta.widget.GhestaWidget

// FragmentActivity (a ComponentActivity) is what the fingerprint prompt needs.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { GhestaRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Widget rows open the item they show (ghesta://item/<id>). */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "ghesta" && data.host == "item") {
            Store.pendingOpenItem = data.lastPathSegment
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
        applyPrivacyFlag()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }

    override fun onResume() {
        super.onResume()
        Store.onResume()
        // Re-arm the daily reminder every time the app is opened (covers aggressive battery managers).
        Reminders.schedule(this)
        Reminders.dismissIfResolved(this)
        GhestaWidget.updateAll(this)
        Sync.requestSync(0)
    }

    /**
     * With the lock on, the app's picture is hidden in the recent-apps list and screenshots are blocked.
     * Debug builds are exempt so the app can still be screenshotted while it is being developed.
     */
    private fun applyPrivacyFlag() {
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (Store.settings.appLock && !debuggable) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
