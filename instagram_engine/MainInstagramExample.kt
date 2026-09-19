package com.instagram.engine

fun main() {
    val engine = InstagramEngine(
        proxyHost = null,
        proxyPort = null
    )

    // 1. Login Username & Password (enc_password #PWD_INSTAGRAM)
    val loginRes = engine.login("my_instagram_username", "MySecurePass123!")
    println(loginRes)

    if (loginRes.isSuccess && loginRes.userId != null) {
        // 2. Hiển thị Avatar (HD Avatar & Profile Pic URL)
        val avatarInfo = engine.getAvatar(loginRes.userId)
        println(avatarInfo)

        // 3. Up Avatar mới (từ mảng byte ảnh)
        // val imageBytes = java.io.File("avatar.jpg").readBytes()
        // val uploadRes = engine.uploadAvatar(imageBytes)
        // println(uploadRes)
    }
}
