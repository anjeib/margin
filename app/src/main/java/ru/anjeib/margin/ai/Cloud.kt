package ru.anjeib.margin.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Whether Margin's cloud answers — not whether Android thinks there is internet. A phone can be «online»
 * on a train or behind a blocked host and still wait twenty seconds for nothing. So Margin knows it before it asks:
 * while the app is open it knocks every half-minute — a bare connection to the cloud's door within a second, nothing
 * sent, nothing billed — and at once when the network changes or typing starts. No answer → it works on the phone
 * (instantly, «без облака» by the day's summary); an answer again → back to the cloud, and what waited in «Входящие» is sorted.
 * Its own requests count too: two that failed or crawled mean the same as a knock unanswered.
 */
object Cloud {
    enum class State { Ok, Down }

    /** A request's own wait for the door — only a guard now: the knock has usually told already. */
    const val CONNECT_MS = 3_000
    /** The knock: a healthy door takes a connection in a fraction of a second, even on 3G. */
    const val KNOCK_MS = 1_000
    /** What was heard from the cloud this recently still holds (a little over the knock's half-minute). */
    private const val FRESH_MS = 45_000L
    /** A phrase is read by the fast model in seconds; a reply this late means the line crawls. */
    const val SLOW_MS = 15_000L
    /** How often the open app knocks. */
    private const val EVERY_MS = 30_000L

    private val _state = MutableStateFlow(State.Ok)
    val state: StateFlow<State> = _state
    @Volatile private var misses = 0
    @Volatile private var heard = 0L
    @Volatile private var knocking: Deferred<Boolean>? = null
    @Volatile private var knockedAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val down get() = _state.value == State.Down

    /** An answer came (any HTTP code: the door is there). A slow one counts half-way towards working on the phone. */
    fun reached(ms: Long, slowMs: Long = SLOW_MS) {
        if (ms > slowMs) miss() else { misses = 0; heard = System.currentTimeMillis(); _state.value = State.Ok }
    }

    /** No answer: the connection failed or timed out. Two in a row → work on the phone. */
    fun miss() {
        misses++
        if (misses >= 2) _state.value = State.Down
    }

    /** Wraps one request: times it and counts it. IOExceptions pass through for the caller's own message. */
    inline fun <T> watch(slowMs: Long = SLOW_MS, block: () -> T): T {
        val t0 = System.currentTimeMillis()
        val r = try { block() } catch (e: IOException) { miss(); throw e }
        reached(System.currentTimeMillis() - t0, slowMs)
        return r
    }

    /** The phone itself has a working network (Android checked it). */
    fun network(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** The cloud answered within the last half-minute: no need to ask again before sending. */
    fun fresh() = System.currentTimeMillis() - heard < FRESH_MS

    /** Where the AI's door is: Margin's own (Yandex Cloud) in what people install, Yandex or OpenRouter with a key. */
    private fun door(s: ru.anjeib.margin.data.Settings): String = when {
        !Llm.yandex(s) -> "openrouter.ai"
        Llm.door(s).proxy -> URL(ru.anjeib.margin.BuildConfig.PROXY_URL).host
        else -> "llm.api.cloud.yandex.net"
    }

    /**
     * One knock: a bare TCP connection to the door within a second — nothing is sent, nothing is billed, the function
     * doesn't even start. It answers → the cloud is there; it doesn't → work on the phone right away.
     */
    suspend fun knock(s: ru.anjeib.margin.data.Settings): Boolean = withContext(Dispatchers.IO) {
        val ok = try {
            Socket().use { it.connect(InetSocketAddress(door(s), 443), KNOCK_MS) }; true
        } catch (e: IOException) { false }
        if (ok) { misses = 0; heard = System.currentTimeMillis(); _state.value = State.Ok } else _state.value = State.Down
        ok
    }

    /** Knocks in the background unless the answer is fresh or a knock is already on its way (typing, coming to the front). */
    fun warm(s: ru.anjeib.margin.data.Settings) {
        val now = System.currentTimeMillis()
        if (fresh() || knocking?.isActive == true || now - knockedAt < 10_000) return
        knockedAt = now
        knocking = scope.async { knock(s) }
    }

    /** Before asking the AI: true at once when known, else waits for a knock — never more than a second. */
    suspend fun check(s: ru.anjeib.margin.data.Settings): Boolean {
        if (down) return false
        if (fresh()) return true
        warm(s)
        return knocking?.await() ?: !down
    }

    /**
     * While the app is open: a knock every half-minute (a bare connection — no AI, nothing billed), at once when the phone
     * changes networks. So whether the cloud answers is always known before anything is sent. [onBack] — it answers again
     * after being down: the caller sorts what waited.
     */
    suspend fun keepWatch(ctx: Context, s: () -> ru.anjeib.margin.data.Settings, onBack: () -> Unit) {
        val changed = MutableStateFlow(0)
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { changed.value++ }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) changed.value++
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        try {
            while (true) {
                val wasDown = down
                if (network(ctx) && Llm.ready(s())) {
                    knockedAt = System.currentTimeMillis()
                    if (knock(s()) && wasDown) onBack()
                }
                val seen = changed.value
                var waited = 0L
                while (waited < EVERY_MS && changed.value == seen) { delay(500); waited += 500 }
                if (changed.value != seen) delay(1_500) // let the new network settle before knocking
            }
        } finally {
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
    }
}
