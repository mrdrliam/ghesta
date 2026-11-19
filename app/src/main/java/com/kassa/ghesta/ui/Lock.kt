package com.kassa.ghesta.ui

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.ui.theme.G

/**
 * App lock. Off by default; when it is on, the app asks for the fingerprint / face / screen lock
 * of the phone when it is opened, and again after it has been away for longer than the grace time.
 */
object AppLock {
    var locked by mutableStateOf(false)
        private set

    private var everUnlocked = false
    private var leftAt = 0L

    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Pure rule, kept separate so it can be unit-tested. */
    fun shouldLock(enabled: Boolean, everUnlocked: Boolean, awayMs: Long, graceSec: Int): Boolean =
        enabled && (!everUnlocked || awayMs > graceSec * 1000L)

    fun onStart() {
        val away = SystemClock.elapsedRealtime() - leftAt
        if (shouldLock(Store.settings.appLock, everUnlocked, away, Store.settings.lockGraceSec)) locked = true
    }

    fun onStop() {
        leftAt = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        locked = false
        everUnlocked = true
    }

    /** Turning the lock on is only allowed when the phone itself has a lock to fall back on. */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTH) // with a screen-lock fallback there is no negative button
            .build()
        prompt.authenticate(info)
    }
}

/** Opaque cover shown while the app is locked. It hides everything and swallows all touches. */
@Composable
fun LockScreen() {
    val c = G.c
    val activity = LocalContext.current as? FragmentActivity
    var failed by remember { mutableStateOf(false) }
    val ask = {
        if (activity != null) {
            failed = false
            AppLock.authenticate(activity, "باز کردن قسطا", onSuccess = { AppLock.unlock() }, onFail = { failed = true })
        }
    }
    LaunchedEffect(Unit) { ask() }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(c.teal2, c.teal, c.tealDeep)))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMark(92)
            T("قسطا", 28, FontWeight.Bold, Color.White)
            Icon(Icons.Filled.Lock, null, tint = c.gold2, modifier = Modifier.size(22.dp))
            T(
                if (failed) "قفل باز نشد. دوباره تلاش کنید." else "برای دیدن اطلاعات، قفل را باز کنید.",
                14, color = Color.White.copy(alpha = 0.8f), align = TextAlign.Center,
            )
            Box(Modifier.height(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(c.gold2, c.gold)))
                    .clickable { ask() }.padding(horizontal = 32.dp, vertical = 14.dp),
            ) { T("باز کردن قفل", 16, FontWeight.Bold, Color(0xFF2A1F05)) }
        }
    }
}
