package com.example.sync

import android.util.Log
import com.example.data.Note
import com.example.data.NoteRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class SyncServer(
    private val repository: NoteRepository,
    private val port: Int = 9090
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val noteListType = Types.newParameterizedType(List::class.java, Note::class.java)
    private val jsonAdapter = moshi.adapter<List<Note>>(noteListType)

    var onStatusChanged: ((String) -> Unit)? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            updateStatus("Server active at http://${getLocalIpAddress()}:$port")
            Log.d("SyncServer", "Sync server started on port $port")
            
            while (isRunning) {
                val socket = serverSocket?.accept() ?: break
                handleClient(socket)
            }
        } catch (e: Exception) {
            Log.e("SyncServer", "Server error", e)
            updateStatus("Error: ${e.localizedMessage}")
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("SyncServer", "Failed to close server socket", e)
        }
        serverSocket = null
        updateStatus("Offline")
    }

    private fun updateStatus(status: String) {
        onStatusChanged?.invoke(status)
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            // Parse request line
            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext
            val method = parts[0]
            val path = parts[1]

            // Parse headers
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Read Body
            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var bytesRead = 0
                while (bytesRead < contentLength) {
                    val read = reader.read(buffer, bytesRead, contentLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
                String(buffer)
            } else ""

            Log.d("SyncServer", "Request: $method $path, Body length: ${body.length}")

            when {
                path == "/" && method == "GET" -> {
                    serveHtmlIndex(out)
                }
                path == "/api/notes" && method == "GET" -> {
                    val notes = repository.getFullNotesListForSync()
                    val json = jsonAdapter.toJson(notes)
                    sendJsonResponse(out, 200, json)
                }
                path == "/api/sync" && method == "POST" -> {
                    try {
                        val incomingNotes = jsonAdapter.fromJson(body)
                        if (incomingNotes != null) {
                            // Run the sync merge locally!
                            val localChanged = repository.syncWithRemoteNotes(incomingNotes)
                            Log.d("SyncServer", "Sync merge done, changed: $localChanged")
                        }
                        // Send our updated list back as response so device B completes key sync
                        val currentNotes = repository.getFullNotesListForSync()
                        val json = jsonAdapter.toJson(currentNotes)
                        sendJsonResponse(out, 200, json)
                    } catch (e: Exception) {
                        Log.e("SyncServer", "Sync processing failed", e)
                        sendJsonResponse(out, 400, "{\"error\":\"Invalid format: ${e.message}\"}")
                    }
                }
                else -> {
                    sendJsonResponse(out, 404, "{\"error\":\"Not Found\"}")
                }
            }
            socket.close()
        } catch (e: Exception) {
            Log.e("SyncServer", "Client handler failed", e)
        }
    }

    private fun serveHtmlIndex(out: OutputStream) {
        val title = "Markdown Notes - Node Service"
        val bodyText = "The localized Notes P2P synchronizer endpoint is active on this device. Use your primary Markdown Notes mobile client to synchronize note sheets."
        val response = """
            HTTP/1.1 200 OK
            Content-Type: text/html; charset=utf-8
            Connection: close
            
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #12131a; color: #e3e1ec; padding: 2rem; }
                    .card { background: #1f2029; padding: 2rem; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); max-width: 500px; margin: auto; border: 1px solid #333546; }
                    h1 { color: #d0bcff; margin-top: 0; }
                    p { line-height: 1.6; color: #cac4d0; }
                    .badge { display: inline-block; background: #4f378b; color: #e8def8; padding: 0.25rem 0.75rem; border-radius: 99px; font-weight: bold; margin-bottom: 1rem; font-size: 0.85rem; }
                </style>
                <title>$title</title>
            </head>
            <body>
                <div class="card">
                    <span class="badge">ACTIVE PORT</span>
                    <h1>$title</h1>
                    <p>$bodyText</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        out.write(response.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendJsonResponse(out: OutputStream, status: Int, json: String) {
        val statusMsg = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val response = """
            HTTP/1.1 $status $statusMsg
            Content-Type: application/json; charset=utf-8
            Content-Length: ${json.toByteArray(Charsets.UTF_8).size}
            Access-Control-Allow-Origin: *
            Connection: close
            
            $json
        """.trimIndent()
        out.write(response.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (networkInterface in interfaces) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (inetAddress in addresses) {
                        if (!inetAddress.isLoopbackAddress) {
                            val hostAddress = inetAddress.hostAddress
                            // Ensure it is an IPv4 address
                            if (hostAddress != null && hostAddress.indexOf(':') < 0) {
                                return hostAddress
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("SyncServer", "IP scanning failed", ex)
            }
            return "127.0.0.1"
        }
    }
}
