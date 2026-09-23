package com.cayxu.app.facebook

import androidx.annotation.Keep

/**
 * FbVault — Cung cấp các chuỗi nhạy cảm của Facebook engine.
 *
 * Dùng string building tại runtime để tránh chuỗi tĩnh trong DEX.
 * Không phụ thuộc native .so — đảm bảo 100% hoạt động đúng.
 */
@Keep
object FbVault {

    // Tránh chuỗi tĩnh duy nhất trong constant pool bằng cách nối từng phần
    // Decompiler sẽ thấy nhiều fragment nhỏ thay vì 1 URL hoàn chỉnh

    fun graphApiUrl()       = "https://" + "graph.face" + "book.com/v21.0"
    fun graphqlUrl()        = "https://" + "graph.face" + "book.com/gra" + "phql"
    fun userAgent()         = "[FBAN/FB4A" + ";FBAV/548.1.0.51.64" +
                              ";FBBV/474618929;FBDM/{density=3.0," +
                              "width=1080,height=2340}" +
                              ";FBLC/vi_VN;FBRV/0;FBCR/Viettel" +
                              ";FBMF/samsung;FBBD/samsung" +
                              ";FBPN/com.face" + "book.katana" +
                              ";FBDV/SM-S928B;FBSV/14" +
                              ";FBOP/1;FBCA/arm64-v8a;]"

    fun docIdPageReact()    = "54117" + "82298894101"
    fun docIdProfileReact() = "54117" + "82298894101"
    fun docIdVoiceReact()   = "47154" + "26135182900"

    fun pathReactions()     = "/" + "reactions"
    fun pathComments()      = "/" + "comments"
    fun pathSubscribers()   = "/" + "subscribers"
    fun pathLikes()         = "/" + "likes"

    fun fieldAccessToken()  = "access" + "_token"
    fun fieldMessage()      = "mes" + "sage"
    fun fieldAttachmentId() = "attach" + "ment_id"
    fun fieldType()         = "ty" + "pe"
    fun fieldVariables()    = "varia" + "bles"
    fun fieldDocId()        = "doc" + "_id"
}
