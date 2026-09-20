package com.cayxu.app.facebook

import androidx.annotation.Keep

/**
 * FbVault — Cung cấp các chuỗi nhạy cảm của Facebook engine.
 *
 * Ưu tiên lấy từ native .so (fb-vault):
 *   - C++ dùng constexpr XOR 0x5C encode tại compile-time
 *   - .rodata của .so chỉ chứa XOR bytes, không có plain text
 *
 * Fallback (khi .so load lỗi) → string split tại runtime, đảm bảo đúng.
 */
@Keep
object FbVault {

    private val nativeOk: Boolean by lazy {
        try { System.loadLibrary("fb-vault"); true }
        catch (_: Throwable) { false }
    }

    // ── JNI declarations ─────────────────────────────────────────────────
    @Keep private external fun nativeGraphApiUrl(): String
    @Keep private external fun nativeGraphqlUrl(): String
    @Keep private external fun nativeUserAgent(): String
    @Keep private external fun nativeDocIdPageReact(): String
    @Keep private external fun nativeDocIdProfileReact(): String
    @Keep private external fun nativePathReactions(): String
    @Keep private external fun nativePathComments(): String
    @Keep private external fun nativePathSubscribers(): String
    @Keep private external fun nativePathLikes(): String
    @Keep private external fun nativeFieldAccessToken(): String
    @Keep private external fun nativeFieldMessage(): String
    @Keep private external fun nativeFieldAttachmentId(): String
    @Keep private external fun nativeFieldType(): String
    @Keep private external fun nativeFieldVariables(): String
    @Keep private external fun nativeFieldDocId(): String

    // ── Fallback: string parts nối lại tại runtime ────────────────────────
    // Tránh 1 constant string duy nhất trong constant pool DEX
    // Đảm bảo 100% đúng — không phụ thuộc vào tính toán thủ công

    private fun fbk_graphApi()    = "htt" + "ps://" + "graph.face" + "book.com/v21.0"
    private fun fbk_graphql()     = "htt" + "ps://" + "graph.face" + "book.com/gra" + "phql"
    private fun fbk_ua()          = "[FBA" + "N/FB4A;FBA" + "V/548.1.0.51.64;FBB" + "V/47461892" +
                                    "9;FBDM/{density=3.0,width=1080,height=2340}" +
                                    ";FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/sam" +
                                    "sung;FBBD/samsung;FBPN/com.face" +
                                    "book.katana;FBDV/SM-S928B;FBSV/14;" +
                                    "FBOP/1;FBCA/arm64-v8a;]"
    private fun fbk_docPage()     = "471542" + "6135182900"
    private fun fbk_docProfile()  = "541178" + "2298894101"
    private fun fbk_reactions()   = "/" + "reac" + "tions"
    private fun fbk_comments()    = "/" + "com" + "ments"
    private fun fbk_subs()        = "/" + "sub" + "scribers"
    private fun fbk_likes()       = "/" + "li" + "kes"
    private fun fbk_accTok()      = "acc" + "ess_" + "token"
    private fun fbk_msg()         = "mes" + "sage"
    private fun fbk_attId()       = "att" + "achment_id"
    private fun fbk_type()        = "ty" + "pe"
    private fun fbk_vars()        = "vari" + "ables"
    private fun fbk_docId()       = "doc" + "_id"

    // ── Public API ────────────────────────────────────────────────────────
    private inline fun get(native: () -> String, fallback: () -> String) =
        if (nativeOk) try { native() } catch (_: Throwable) { fallback() } else fallback()

    fun graphApiUrl()       = get({ nativeGraphApiUrl()        }, { fbk_graphApi()   })
    fun graphqlUrl()        = get({ nativeGraphqlUrl()         }, { fbk_graphql()    })
    fun userAgent()         = get({ nativeUserAgent()          }, { fbk_ua()         })
    fun docIdPageReact()    = get({ nativeDocIdPageReact()     }, { fbk_docPage()    })
    fun docIdProfileReact() = get({ nativeDocIdProfileReact()  }, { fbk_docProfile() })
    fun pathReactions()     = get({ nativePathReactions()      }, { fbk_reactions()  })
    fun pathComments()      = get({ nativePathComments()       }, { fbk_comments()   })
    fun pathSubscribers()   = get({ nativePathSubscribers()    }, { fbk_subs()       })
    fun pathLikes()         = get({ nativePathLikes()          }, { fbk_likes()      })
    fun fieldAccessToken()  = get({ nativeFieldAccessToken()   }, { fbk_accTok()     })
    fun fieldMessage()      = get({ nativeFieldMessage()       }, { fbk_msg()        })
    fun fieldAttachmentId() = get({ nativeFieldAttachmentId()  }, { fbk_attId()      })
    fun fieldType()         = get({ nativeFieldType()          }, { fbk_type()       })
    fun fieldVariables()    = get({ nativeFieldVariables()     }, { fbk_vars()       })
    fun fieldDocId()        = get({ nativeFieldDocId()         }, { fbk_docId()      })
}
