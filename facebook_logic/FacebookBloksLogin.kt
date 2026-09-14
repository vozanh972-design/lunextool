package com.cayxu.app.facebook

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Trích xuất từ class `Lk1/c;` và `Li2/l0;` trong APK.
 * Đăng nhập Facebook bằng User/Pass/2FA qua giao thức Meta CAA Bloks GraphQL.
 */
class FacebookBloksLogin {

    companion object {
        const val GRAPHQL_ENDPOINT = "https://b-graph.facebook.com/graphql"
        const val PWD_KEY_FETCH_ENDPOINT = "https://b-graph.facebook.com/pwd_key_fetch"
        const val OAUTH_TOKEN = "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32"

        const val APP_ID_LOGIN = "com.bloks.www.bloks.caa.login.async.send_login_request"
        const val BLOKS_VERSIONING_ID = "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6"
        const val CLIENT_DOC_ID = "119940804214876861379510865434"

        const val FB_KATANA_UA = "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/x86_64:arm64-v8a;]"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Lấy public key mã hóa mật khẩu của Meta
     */
    @Throws(Exception::class)
    fun fetchPasswordEncryptionKey(): String {
        val request = Request.Builder()
            .url(PWD_KEY_FETCH_ENDPOINT)
            .header("User-Agent", FB_KATANA_UA)
            .header("Authorization", OAUTH_TOKEN)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    /**
     * Gửi request đăng nhập CAA Bloks
     */
    @Throws(Exception::class)
    fun executeLogin(
        username: String,
        passwordEncrypted: String,
        deviceId: String,
        familyDeviceId: String,
        waterfallId: String
    ): String {
        val clientInputParams = JSONObject().apply {
            put("contact_point", username)
            put("password", passwordEncrypted)
            put("device_id", deviceId)
            put("family_device_id", familyDeviceId)
            put("login_source", "Login")
            put("waterfall_id", waterfallId)
            put("credential_type", "password")
            put("event_flow", "login_manual")
            put("login_attempt_count", "1")
            put("access_flow_version", "F2_FLOW")
            put("is_caa_perf_enabled", true)
        }

        val serverParams = JSONObject().apply {
            put("credential_type", "password")
            put("server_login_source", "login")
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        val formBody = FormBody.Builder()
            .add("params", rootParams.toString())
            .add("bloks_versioning_id", BLOKS_VERSIONING_ID)
            .add("app_id", APP_ID_LOGIN)
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-$APP_ID_LOGIN")
            .add("fb_api_caller_class", "graphservice")
            .add("client_doc_id", CLIENT_DOC_ID)
            .add("method", "post")
            .add("format", "json")
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", FB_KATANA_UA)
            .header("Authorization", OAUTH_TOKEN)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Tigon/Liger")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }
}
