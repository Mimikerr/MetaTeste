package com.example.metateste.nexus.network

import com.example.metateste.shared.Heartbeat
import com.example.metateste.shared.HeartbeatAck
import com.example.metateste.shared.Hello
import com.example.metateste.shared.NexusMessage
import com.example.metateste.shared.decodeNexusMessage
import com.example.metateste.shared.encode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Maintains a persistent WebSocket connection to the Nexus host, reconnecting
 * with exponential backoff whenever the connection drops.
 */
class NexusWebSocketClient(
    private val deviceId: String,
    private val hostProvider: () -> String,
    private val portProvider: () -> Int,
) {
    private val client = HttpClient(CIO) { install(WebSockets) }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incoming = MutableSharedFlow<NexusMessage>(extraBufferCapacity = 16)
    val incoming: SharedFlow<NexusMessage> = _incoming.asSharedFlow()

    /** Round-trip time of the last answered heartbeat, in ms; null while disconnected or before the first reply. */
    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val pendingHeartbeats = ConcurrentHashMap<String, Long>()

    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null

    /** Runs forever, reconnecting with exponential backoff until the coroutine is cancelled. */
    suspend fun run() {
        var backoff = 1.seconds
        while (currentCoroutineContext().isActive) {
            _connectionState.value = ConnectionState.CONNECTING
            try {
                client.webSocket(host = hostProvider(), port = portProvider(), path = "/nexus") {
                    activeSession = this
                    _connectionState.value = ConnectionState.CONNECTED
                    backoff = 1.seconds
                    val hello = Hello(
                        messageId = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        deviceId = deviceId,
                        deviceName = android.os.Build.MODEL,
                        appVersion = "0.1.0",
                    )
                    send(Frame.Text(hello.encode()))

                    val heartbeatJob = launch {
                        while (isActive) {
                            delay(HEARTBEAT_INTERVAL)
                            val heartbeatId = UUID.randomUUID().toString()
                            pendingHeartbeats[heartbeatId] = System.currentTimeMillis()
                            send(Frame.Text(Heartbeat(messageId = heartbeatId, timestamp = System.currentTimeMillis()).encode()))
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = runCatching { frame.readText().decodeNexusMessage() }.getOrNull()
                                if (message is HeartbeatAck) {
                                    pendingHeartbeats.remove(message.correlatesTo)?.let { sentAt ->
                                        _latencyMs.value = System.currentTimeMillis() - sentAt
                                    }
                                } else if (message != null) {
                                    _incoming.emit(message)
                                }
                            }
                        }
                    } finally {
                        heartbeatJob.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // connection dropped or failed to establish; fall through to backoff + retry
            } finally {
                activeSession = null
                _connectionState.value = ConnectionState.DISCONNECTED
                pendingHeartbeats.clear()
                _latencyMs.value = null
            }
            delay(backoff)
            _connectionState.value = ConnectionState.RECONNECTING
            backoff = (backoff * 2).let { if (it > 30.seconds) 30.seconds else it }
        }
    }

    suspend fun send(message: NexusMessage) {
        val session = activeSession ?: return
        runCatching { session.send(Frame.Text(message.encode())) }
    }

    private companion object {
        val HEARTBEAT_INTERVAL = 5.seconds
    }
}
