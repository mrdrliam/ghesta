package com.kassa.ghesta.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncState { Idle, Syncing, Offline, Error }

/**
 * Keeps the phone and the server in step. Local-first: the app never waits for the network.
 * Pushes pending changes and pulls everything newer than the cursor in one round trip.
 */
object Sync {
    var state by mutableStateOf(SyncState.Idle)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** Epoch millis of the last successful sync, 0 if never. */
    var lastSync by mutableLongStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var debounce: Job? = null

    /** Schedules a sync soon; many quick edits collapse into one request. No-op when signed out. */
    fun requestSync(delayMs: Long = 1500) {
        if (!Account.signedIn) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    fun reset() {
        debounce?.cancel()
        state = SyncState.Idle
        lastError = null
        lastSync = 0
    }

    /** Returns true when the phone and server are in step afterwards. */
    suspend fun syncNow(): Boolean {
        if (!Account.signedIn || Account.awaitingDataChoice) return false
        return mutex.withLock {
            state = SyncState.Syncing
            try {
                var rounds = 0
                do {
                    val batch = Store.buildBatch()
                    val req = org.json.JSONObject().put("cursor", Store.syncCursor).put("changes", batch.changes)
                    val resp = Account.authed("POST", "/v1/sync", req)

                    val accepted = HashSet<String>()
                    resp.optJSONArray("accepted")?.let { a -> for (i in 0 until a.length()) accepted.add(a.getString(i)) }
                    val remote = ArrayList<RemoteChange>()
                    resp.optJSONArray("changes")?.let { a ->
                        for (i in 0 until a.length()) {
                            val o = a.getJSONObject(i)
                            remote.add(RemoteChange(o.getString("id"), o.getLong("updated_at"), o.optBoolean("deleted"), o.optJSONObject("data")))
                        }
                    }
                    Store.applySync(batch, accepted, remote, resp.getLong("cursor"))
                    rounds++
                } while (Store.hasPending() && rounds < 10 && Account.signedIn)
                lastSync = System.currentTimeMillis()
                lastError = null
                state = SyncState.Idle
                true
            } catch (e: ApiException) {
                if (e.offline) {
                    state = SyncState.Offline
                    lastError = null
                } else {
                    state = SyncState.Error
                    lastError = e.message
                }
                false
            } catch (e: Exception) {
                state = SyncState.Error
                lastError = "خطا در همگام‌سازی"
                false
            }
        }
    }
}
