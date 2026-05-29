package com.example.bilibili.ui.message

import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MessageSseClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var call: Call? = null

    fun start(onEvent: (JSONObject) -> Unit) {
        stop()
        val token = SPUtils.getToken()
        if (token.isEmpty()) return
        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}message/sse/subscribe")
            .header("webToken", token)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        call = client.newCall(request)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 连接失败时由页面 onResume 拉取未读数
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    response.close()
                    return
                }
                val source = response.body?.source() ?: run {
                    response.close()
                    return
                }
                readStream(source, onEvent)
                response.close()
            }
        })
    }

    private fun readStream(source: BufferedSource, onEvent: (JSONObject) -> Unit) {
        var eventName = "message"
        val dataBuilder = StringBuilder()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> dataBuilder.append(line.removePrefix("data:").trim())
                    line.isEmpty() -> {
                        if (dataBuilder.isNotEmpty()) {
                            runCatching {
                                val json = JSONObject(dataBuilder.toString())
                                onEvent(json)
                            }
                        }
                        dataBuilder.clear()
                        eventName = "message"
                    }
                }
            }
        } catch (_: IOException) {
            // stream closed
        }
    }

    fun stop() {
        call?.cancel()
        call = null
    }
}
