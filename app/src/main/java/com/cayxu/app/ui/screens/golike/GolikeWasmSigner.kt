package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Trình ký chữ ký bảo mật WebAssembly (WASM sign_v3) chuẩn Golike Web.
 * Sử dụng file golike_signer.wasm trích xuất từ web Golike để tạo header g-auth cho gweb-v3.
 */
object GolikeWasmSigner {
    private var webView: WebView? = null
    @Volatile
    private var isWasmReady = false
    private var readyDeferred = CompletableDeferred<Boolean>()
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun init(context: Context) {
        if (webView != null && isWasmReady) return
        mainHandler.post {
            if (webView != null && isWasmReady) return@post
            try {
                readyDeferred = CompletableDeferred()
                val wv = WebView(context.applicationContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.addJavascriptInterface(JsBridge(), "AndroidSigner")

                val wasmBytes = try {
                    context.assets.open("golike_signer.wasm").use { it.readBytes() }
                } catch (e: Exception) {
                    android.util.Log.e("GolikeWasmSigner", "Không thể đọc file assets/golike_signer.wasm: ${e.message}")
                    ByteArray(0)
                }

                val wasmB64 = Base64.encodeToString(wasmBytes, Base64.NO_WRAP)

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <script>
                    const WASM_B64 = "$wasmB64";
                    let wasmInstance = null;
                    const uD = new TextEncoder();

                    function lD(t, e, n) {
                        const o = n.map(h => uD.encode(String(h)));
                        let i = 32 + 12;
                        for (const h of o) i += 4 + h.length;
                        const a = new Uint8Array(i);
                        const c = new DataView(a.buffer);
                        let f = 0;
                        a.set(t, f); f += 32;
                        a.set(e, f); f += 12;
                        for (const h of o) {
                            c.setUint32(f, h.length, true);
                            f += 4;
                            a.set(h, f);
                            f += h.length;
                        }
                        return a;
                    }

                    function b64ToBytes(s) {
                        const b = atob(s.replace(/-/g, '+').replace(/_/g, '/'));
                        const a = new Uint8Array(b.length);
                        for (let i = 0; i < b.length; i++) a[i] = b.charCodeAt(i);
                        return a;
                    }

                    async function initWasm() {
                        try {
                            const raw = atob(WASM_B64);
                            const bytes = new Uint8Array(raw.length);
                            for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
                            const res = await WebAssembly.instantiate(bytes, {});
                            wasmInstance = res.instance.exports;
                            AndroidSigner.onReady(true);
                        } catch (err) {
                            AndroidSigner.onReady(false);
                        }
                    }

                    window.sign = function(callId, rawKeyB64, ivB64, t, x, d, u, n, k, q) {
                        try {
                            if (!wasmInstance) {
                                AndroidSigner.onResult(callId, "");
                                return;
                            }
                            const rawKey = b64ToBytes(rawKeyB64);
                            const iv = b64ToBytes(ivB64);
                            const p = lD(rawKey, iv, [t, x, d, u, n, k, q]);
                            const xPtr = wasmInstance.alloc(4);
                            const uPtr = wasmInstance.alloc(p.length);
                            new Uint8Array(wasmInstance.memory.buffer).set(p, uPtr);
                            let outPtr;
                            try {
                                outPtr = wasmInstance.sign_v3(uPtr, p.length, xPtr);
                            } finally {
                                wasmInstance.dealloc(uPtr, p.length);
                            }
                            const outLen = new DataView(wasmInstance.memory.buffer).getUint32(xPtr, true);
                            wasmInstance.dealloc(xPtr, 4);
                            const outBytes = new Uint8Array(wasmInstance.memory.buffer, outPtr, outLen).slice();
                            wasmInstance.dealloc(outPtr, outLen);
                            const result = new TextDecoder().decode(outBytes);
                            AndroidSigner.onResult(callId, result);
                        } catch (e) {
                            AndroidSigner.onResult(callId, "");
                        }
                    };

                    window.onload = initWasm;
                    </script>
                    </head>
                    <body></body>
                    </html>
                """.trimIndent()

                wv.loadDataWithBaseURL("https://app.golike.net/", html, "text/html", "UTF-8", null)
                webView = wv
            } catch (e: Exception) {
                android.util.Log.e("GolikeWasmSigner", "Khởi tạo WebView WASM thất bại: ${e.message}")
                readyDeferred.complete(false)
            }
        }
    }

    private class JsBridge {
        @JavascriptInterface
        fun onReady(success: Boolean) {
            isWasmReady = success
            readyDeferred.complete(success)
        }

        @JavascriptInterface
        fun onResult(callId: String, result: String) {
            pendingCalls.remove(callId)?.complete(result)
        }
    }

    suspend fun sign(
        context: Context,
        rawKeyB64: String,
        timestamp: Long,
        nonceB64: String,
        deviceId: String,
        userId: Long,
        method: String,
        path: String,
        bodyHash: String
    ): String? = withContext(Dispatchers.IO) {
        init(context)
        if (!isWasmReady) {
            withTimeoutOrNull(8000L) { readyDeferred.await() }
        }
        if (!isWasmReady) {
            android.util.Log.w("GolikeWasmSigner", "WASM chưa sẵn sàng sau khi chờ!")
            return@withContext null
        }

        val callId = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pendingCalls[callId] = deferred

        val ivBytes = ByteArray(12)
        SecureRandom().nextBytes(ivBytes)
        val ivB64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP or Base64.URL_SAFE)

        mainHandler.post {
            val script = "window.sign('$callId', '$rawKeyB64', '$ivB64', $timestamp, '$nonceB64', '$deviceId', $userId, '$method', '$path', '$bodyHash');"
            webView?.evaluateJavascript(script, null)
        }

        withTimeoutOrNull(8000L) { deferred.await() }?.takeIf { it.isNotBlank() }
    }
}
