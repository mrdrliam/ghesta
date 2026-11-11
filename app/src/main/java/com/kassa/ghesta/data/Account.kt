package com.kassa.ghesta.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** [status] 0 means "could not reach the server". [code] is the server's stable error code. */
class ApiException(val status: Int, val code: String, message: String) : Exception(message) {
    val offline: Boolean get() = status == 0
}

/** Minimal JSON-over-HTTP client (no extra libraries needed). */
object Api {
    suspend fun call(base: String, method: String, path: String, body: JSONObject?, token: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = try {
                URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw ApiException(0, "bad_url", "آدرس سرور درست نیست.")
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Accept", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                if (status !in 200..299) {
                    throw ApiException(status, json.optString("error", "http_$status"), json.optString("message", "خطای سرور ($status)"))
                }
                json
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException(0, "offline", "اتصال به سرور برقرار نشد. اینترنت و آدرس سرور را بررسی کنید.")
            } finally {
                conn.disconnect()
            }
        }
}

/** Keeps secrets (tokens) encrypted with a key that never leaves the Android Keystore. */
private object SecureStore {
    private const val ALIAS = "ghesta_tokens"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun put(ctx: Context, name: String, value: String?) {
        val prefs = ctx.getSharedPreferences("ghesta_secure", Context.MODE_PRIVATE)
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(ctx: Context, name: String): String? = try {
        val s = ctx.getSharedPreferences("ghesta_secure", Context.MODE_PRIVATE).getString(name, null) ?: return null
        val raw = Base64.decode(s, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

class Session(val username: String, val access: String, val refresh: String)

/** The signed-in account: tokens, server address, and every call that talks about identity. */
object Account {
    var session by mutableStateOf<Session?>(null)
        private set
    var serverUrl by mutableStateOf("")
        private set

    /** True between a successful login and the user's decision about data already on the phone. */
    var awaitingDataChoice by mutableStateOf(false)

    val signedIn: Boolean get() = session != null

    private var ctx: Context? = null
    private val refreshLock = Mutex()

    private fun prefs() = ctx!!.getSharedPreferences("ghesta_account", Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // 10.0.2.2 is how the Android emulator reaches the developer machine
        serverUrl = prefs().getString("server", null) ?: if (debuggable) "http://10.0.2.2:8080" else ""
        SecureStore.get(ctx!!, "session")?.let {
            try {
                val o = JSONObject(it)
                session = Session(o.getString("u"), o.getString("a"), o.getString("r"))
            } catch (_: Exception) {
            }
        }
    }

    fun setServer(raw: String) {
        var u = raw.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        serverUrl = u
        prefs().edit().putString("server", u).apply()
    }

    private fun saveSession(s: Session?) {
        session = s
        SecureStore.put(
            ctx!!, "session",
            s?.let { JSONObject().put("u", it.username).put("a", it.access).put("r", it.refresh).toString() },
        )
    }

    private fun requireServer() {
        if (serverUrl.isBlank()) throw ApiException(0, "no_server", "آدرس سرور را وارد کنید.")
    }

    private fun adopt(r: JSONObject) = saveSession(Session(r.getString("username"), r.getString("access_token"), r.getString("refresh_token")))

    /** Creates the account, signs in, and returns the one-time recovery code. */
    suspend fun register(username: String, password: String): String {
        requireServer()
        val r = Api.call(serverUrl, "POST", "/v1/auth/register", JSONObject().put("username", username).put("password", password), null)
        adopt(r)
        return r.optString("recovery_code")
    }

    suspend fun login(username: String, password: String) {
        requireServer()
        adopt(Api.call(serverUrl, "POST", "/v1/auth/login", JSONObject().put("username", username).put("password", password), null))
    }

    /** Sets a new password with the recovery code; returns the NEW recovery code. */
    suspend fun recover(username: String, code: String, newPassword: String): String {
        requireServer()
        val r = Api.call(
            serverUrl, "POST", "/v1/auth/recover",
            JSONObject().put("username", username).put("recovery_code", code).put("new_password", newPassword), null,
        )
        adopt(r)
        return r.optString("recovery_code")
    }

    /** Signs out. Data stays on the phone but is no longer tied to any account. */
    suspend fun logout() {
        val s = session
        if (s != null) {
            try {
                Api.call(serverUrl, "POST", "/v1/auth/logout", JSONObject(), s.access)
            } catch (_: Exception) {
                // best effort; the token simply expires on the server
            }
        }
        endSession()
    }

    fun endSession() {
        saveSession(null)
        awaitingDataChoice = false
        Store.resetSyncState()
        Sync.reset()
    }

    suspend fun changePassword(old: String, new: String) {
        authed("POST", "/v1/auth/change-password", JSONObject().put("old_password", old).put("new_password", new))
    }

    suspend fun deleteAccount(password: String) {
        authed("POST", "/v1/account/delete", JSONObject().put("password", password))
        endSession()
    }

    /** Authenticated call that transparently refreshes an expired access token once. */
    suspend fun authed(method: String, path: String, body: JSONObject?): JSONObject {
        val s = session ?: throw ApiException(401, "unauthorized", "وارد حساب نشده‌اید.")
        return try {
            Api.call(serverUrl, method, path, body, s.access)
        } catch (e: ApiException) {
            if (e.code != "token_expired") throw e
            refresh(s.access)
            Api.call(serverUrl, method, path, body, session?.access ?: throw e)
        }
    }

    private suspend fun refresh(failedAccess: String) = refreshLock.withLock {
        val cur = session ?: throw ApiException(401, "unauthorized", "وارد حساب نشده‌اید.")
        if (cur.access != failedAccess) return@withLock // someone else already refreshed
        try {
            val r = Api.call(serverUrl, "POST", "/v1/auth/refresh", JSONObject().put("refresh_token", cur.refresh), null)
            saveSession(Session(cur.username, r.getString("access_token"), r.getString("refresh_token")))
        } catch (e: ApiException) {
            if (e.code == "invalid_refresh") {
                endSession()
                throw ApiException(401, "session_ended", "نشست شما تمام شده؛ دوباره وارد شوید.")
            }
            throw e
        }
    }
}
