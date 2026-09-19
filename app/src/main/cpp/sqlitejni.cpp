#include <jni.h>
#include <string>
#include <vector>
#include <stdint.h>
#include <cstdlib>
#include <sstream>
#include <cstdio>

// ============================================================================
// Module: sqlitejni (libsqlitejni.so) - OLLVM Hardened Engine
// Control Flow Flattening (CFF) + Instruction Substitution (MBA) + XOR String Encryption
// ============================================================================

namespace {

__attribute__((always_inline)) static inline std::string decryptOllvmString(const uint8_t* data, size_t len, uint8_t baseKey, uint8_t step) {
    std::string result;
    result.resize(len);
    
    // Control Flow Flattening state machine with MBA (Mixed Boolean-Arithmetic)
    volatile uint32_t state = 0xA1B2C3D4;
    size_t idx = 0;
    
    while (state != 0) {
        switch (state) {
            case 0xA1B2C3D4: { // Init stage
                idx = 0;
                state = 0x5E6F7A8B;
                break;
            }
            case 0x5E6F7A8B: { // Loop condition
                if (idx < len) {
                    state = 0x9C8D7E6F;
                } else {
                    state = 0x11223344;
                }
                break;
            }
            case 0x9C8D7E6F: { // Decryption block with Instruction Substitution
                uint8_t k = (baseKey + (uint8_t)(idx * step)) & 0xFF;
                // MBA: x ^ k == (x | k) - (x & k)
                uint8_t raw = data[idx];
                uint8_t dec = (raw | k) - (raw & k);
                result[idx] = (char)dec;
                idx++;
                state = 0x5E6F7A8B;
                break;
            }
            case 0x11223344: { // Termination stage
                state = 0; // exit
                break;
            }
            default: {
                state = 0;
                break;
            }
        }
    }
    return result;
}

// Encrypted Byte Arrays (Zero plain-text strings in binary .so)
const uint8_t enc_sqliteSec[64] = { 0x65, 0x05, 0x59, 0x10, 0x4F, 0x1C, 0xB7, 0xE8, 0xA4, 0xF9, 0x94, 0xCD, 0x84, 0xD6, 0x8C, 0xA3, 0xF4, 0xB6, 0xEF, 0x82, 0xD9, 0x8B, 0xC1, 0x9C, 0x3D, 0x69, 0x22, 0x7A, 0x12, 0x42, 0x1A, 0x53, 0x0A, 0x22, 0x72, 0x33, 0x69, 0x3C, 0x55, 0x09, 0x41, 0x1E, 0xB5, 0xEF, 0xA9, 0xF6, 0xAE, 0xC7, 0x9E, 0xD0, 0x8E, 0xA5, 0xFE, 0xAA, 0xEE, 0xBB, 0xD4, 0x8A, 0xC3, 0x9B, 0x33, 0x64, 0x3B, 0x70 };
const uint8_t enc_fbOAuth[51] = { 0x13, 0x22, 0x1F, 0x05, 0x10, 0x5F, 0xB5, 0xB8, 0xA4, 0xAD, 0x9A, 0x9C, 0x85, 0x84, 0x8F, 0xF2, 0xFE, 0xEB, 0xA6, 0xD7, 0xDA, 0x89, 0xCE, 0x9E, 0x61, 0x32, 0x74, 0x2E, 0x14, 0x45, 0x1F, 0x07, 0x5A, 0x7B, 0x7E, 0x32, 0x69, 0x6D, 0x55, 0x0E, 0x17, 0x49, 0xB1, 0xBD, 0xA3, 0xA0, 0xFF, 0x91, 0xCD, 0x80, 0x88 };
const uint8_t enc_fbAppToken[45] = { 0x6F, 0x56, 0x5A, 0x47, 0x40, 0x4A, 0xB3, 0xBE, 0xA5, 0xAC, 0x90, 0x91, 0xCC, 0x81, 0x8C, 0xA3, 0xF4, 0xB0, 0xBF, 0xD8, 0x8E, 0xD8, 0xC2, 0x9F, 0x35, 0x39, 0x74, 0x21, 0x14, 0x44, 0x1F, 0x07, 0x0F, 0x20, 0x29, 0x63, 0x6B, 0x6B, 0x55, 0x5A, 0x15, 0x4F, 0xE3, 0xBA, 0xA2 };
const uint8_t enc_fbApiKey[32] = { 0x64, 0x5B, 0x58, 0x10, 0x40, 0x4B, 0xBF, 0xBD, 0xA7, 0xAD, 0x93, 0xCD, 0xD1, 0x8E, 0x86, 0xF2, 0xFC, 0xE1, 0xB8, 0x87, 0xD1, 0xD8, 0x97, 0xCD, 0x36, 0x3A, 0x76, 0x7D, 0x43, 0x16, 0x1A, 0x51 };
const uint8_t enc_fbSig[32] = { 0x6E, 0x52, 0x5E, 0x41, 0x4C, 0x46, 0xE4, 0xB4, 0xF2, 0xAA, 0x95, 0xCA, 0x83, 0x8F, 0xDC, 0xA1, 0xFB, 0xE5, 0xED, 0x85, 0x8D, 0xDA, 0xC5, 0xCA, 0x31, 0x39, 0x70, 0x2C, 0x13, 0x1E, 0x1A, 0x03 };
const uint8_t enc_fbKeyFetch[48] = { 0x68, 0x50, 0x52, 0x40, 0x4C, 0x4D, 0xB6, 0xBA, 0xAD, 0xAD, 0x9B, 0x9D, 0x84, 0x82, 0x8A, 0xB9, 0xAA, 0xB0, 0xEA, 0x80, 0xDF, 0x8C, 0x97, 0x9C, 0x30, 0x32, 0x70, 0x28, 0x19, 0x15, 0x48, 0x03, 0x08, 0x25, 0x7C, 0x37, 0x6D, 0x3E, 0x50, 0x09, 0x4D, 0x4D, 0xB6, 0xBA, 0xF2, 0xF5, 0xAC, 0x9D };
const uint8_t enc_fbDocId[30] = { 0x6D, 0x52, 0x53, 0x48, 0x4C, 0x4F, 0xBE, 0xBD, 0xA0, 0xA9, 0x93, 0x9D, 0x88, 0x80, 0x88, 0xFD, 0xFA, 0xE2, 0xE9, 0xD6, 0xD1, 0xDA, 0xC7, 0xCD, 0x3C, 0x3D, 0x27, 0x2D, 0x13, 0x13 };
const uint8_t enc_fbKatanaUa[219] = { 0x07, 0x25, 0x28, 0x30, 0x36, 0x50, 0xC0, 0xCF, 0xA0, 0xDA, 0x99, 0xEF, 0xF2, 0xF6, 0xE8, 0xEA, 0xF9, 0xE7, 0xE8, 0xCF, 0xD8, 0xC1, 0xC6, 0xD3, 0x30, 0x3D, 0x3C, 0x28, 0x15, 0x16, 0x15, 0x73, 0x7E, 0x01, 0x1C, 0x7E, 0x60, 0x6B, 0x56, 0x5E, 0x47, 0x43, 0xB5, 0xB1, 0xA9, 0xAC, 0xD8, 0xE7, 0xE8, 0xFE, 0x95, 0xBA, 0xAC, 0xAA, 0xB8, 0xAE, 0x8D, 0x9F, 0x8B, 0xC4, 0x30, 0x29, 0x39, 0x20, 0x30, 0x54, 0x43, 0x55, 0x4C, 0x57, 0x7B, 0x7E, 0x64, 0x6B, 0x4E, 0x01, 0x15, 0x1E, 0x19, 0xED, 0xF8, 0xAE, 0xAF, 0x95, 0x98, 0xD2, 0x8D, 0xFB, 0x86, 0x87, 0x91, 0xF6, 0x96, 0x8E, 0xB1, 0xA3, 0xB2, 0x38, 0x4C, 0x53, 0x4A, 0x49, 0x09, 0x1D, 0x0F, 0x7D, 0x00, 0x0A, 0x02, 0x78, 0x13, 0x0A, 0x0E, 0x1A, 0x3C, 0xEE, 0xE6, 0xEA, 0xAD, 0xDB, 0xE6, 0xE6, 0xF4, 0x96, 0x8D, 0x93, 0xA1, 0xBA, 0xB0, 0xCE, 0xA7, 0x90, 0x80, 0xC4, 0x40, 0x4F, 0x56, 0x5F, 0x0D, 0x64, 0x64, 0x58, 0x51, 0x29, 0x61, 0x1E, 0x3B, 0x19, 0x53, 0x29, 0x34, 0x2D, 0xCA, 0xA4, 0xF1, 0xF6, 0xCD, 0x89, 0xC8, 0xD4, 0xDF, 0xA6, 0xA8, 0xBE, 0xB7, 0xB4, 0xC8, 0x86, 0x95, 0x8F, 0x63, 0x67, 0x71, 0x2C, 0x58, 0x67, 0x68, 0x65, 0x15, 0x0C, 0x1C, 0x20, 0x39, 0x31, 0x49, 0x26, 0x13, 0x01, 0xBB, 0xC1, 0xCC, 0xC6, 0xCA, 0x8C, 0x93, 0x8A, 0xFE, 0xFD, 0x89, 0x9D, 0xFB, 0xEA, 0xD9, 0xAF, 0xB2, 0xB4, 0xBF, 0x2A, 0x6D, 0x61, 0x77, 0x17, 0x1C, 0x02, 0x40, 0x05, 0x25, 0x70, 0x0F };
const uint8_t enc_fbDalvikUa[78] = { 0x18, 0x02, 0x06, 0x07, 0x11, 0x14, 0xA9, 0xBF, 0xBA, 0xAA, 0x8C, 0x99, 0x90, 0x9F, 0xF2, 0xAC, 0xA2, 0xA6, 0xA2, 0xDA, 0xC8, 0xBA, 0xCD, 0xDD, 0x45, 0x65, 0x76, 0x6B, 0x4F, 0x4E, 0x4A, 0x15, 0x05, 0x78, 0x6A, 0x63, 0x6B, 0x6E, 0x57, 0x5E, 0x26, 0x30, 0xC1, 0xBF, 0xD3, 0xBE, 0xBE, 0xFE, 0xEA, 0xF1, 0xFB, 0x8F, 0xE7, 0x89, 0x94, 0xE9, 0xA5, 0xD0, 0xB4, 0xBB, 0x41, 0x51, 0x21, 0x21, 0x2D, 0x14, 0x04, 0x01, 0x16, 0x0F, 0x68, 0x7E, 0x67, 0x75, 0x54, 0x5C, 0x4B, 0x2A };

static inline std::string getSqliteSecret() { return decryptOllvmString(enc_sqliteSec, 64, 0x5C, 7); }
static inline std::string getFbOAuthToken() { return decryptOllvmString(enc_fbOAuth, 51, 0x5C, 7); }
static inline std::string getFbAppToken() { return decryptOllvmString(enc_fbAppToken, 45, 0x5C, 7); }
static inline std::string getFbApiKey() { return decryptOllvmString(enc_fbApiKey, 32, 0x5C, 7); }
static inline std::string getFbSig() { return decryptOllvmString(enc_fbSig, 32, 0x5C, 7); }
static inline std::string getFbKeyFetchToken() { return decryptOllvmString(enc_fbKeyFetch, 48, 0x5C, 7); }
static inline std::string getFbBloksDocId() { return decryptOllvmString(enc_fbDocId, 30, 0x5C, 7); }
static inline std::string getFbKatanaUA() { return decryptOllvmString(enc_fbKatanaUa, 219, 0x5C, 7); }
static inline std::string getFbDalvikUA() { return decryptOllvmString(enc_fbDalvikUa, 78, 0x5C, 7); }


// --- TIKTOK CORE HARDENED ARRAYS (OLLVM CFF + MBA) ---
const uint8_t enc_ttBaseUrl[22] = { 0x34, 0x17, 0x1E, 0x01, 0x0B, 0x45, 0xA9, 0xA2, 0xE3, 0xEC, 0xD5, 0x87, 0xC4, 0xDE, 0xD5, 0xB1, 0xA3, 0xB8, 0xF4, 0x82, 0x87, 0x82 };
const uint8_t enc_ttTagUniversal[34] = { 0x03, 0x3C, 0x3F, 0x3F, 0x31, 0x29, 0xC3, 0xDF, 0xC7, 0xDA, 0xEE, 0xF6, 0xF4, 0xF6, 0xEA, 0x84, 0x93, 0x95, 0x95, 0xB3, 0xB7, 0xBD, 0xB3, 0xB5, 0x5D, 0x4F, 0x40, 0x58, 0x74, 0x6E, 0x61, 0x7B, 0x63, 0x1C };
const uint8_t enc_ttTagSigi[10] = { 0x0F, 0x2A, 0x2D, 0x38, 0x27, 0x2C, 0xD2, 0xCC, 0xC0, 0xDE };
const uint8_t enc_ttMobileUa[135] = { 0x11, 0x0C, 0x10, 0x18, 0x14, 0x13, 0xE7, 0xA2, 0xA1, 0xB5, 0x92, 0x89, 0x98, 0xDE, 0xEE, 0xAD, 0xA3, 0xBD, 0xBF, 0xDA, 0xC8, 0xAC, 0xA6, 0xA8, 0x24, 0x62, 0x42, 0x71, 0x4F, 0x49, 0x4B, 0x15, 0x73, 0x10, 0x6A, 0x60, 0x6E, 0x00, 0x50, 0x4D, 0x18, 0x12, 0xE9, 0xEC, 0xB0, 0xDA, 0xFF, 0xC6, 0x8C, 0xFC, 0xE9, 0xE1, 0x90, 0xE6, 0xF6, 0x9C, 0x94, 0x9B, 0x9E, 0x9C, 0x57, 0x62, 0x6C, 0x5E, 0x75, 0x57, 0x05, 0x07, 0x08, 0x0A, 0x68, 0x7C, 0x7A, 0x6A, 0x57, 0x49, 0x58, 0x3C, 0x36, 0xD1, 0xC1, 0xDF, 0xB6, 0x81, 0xC4, 0xC6, 0xDD, 0xD8, 0xE4, 0x8C, 0xB7, 0xBA, 0x8B, 0x88, 0xC7, 0xD5, 0xAA, 0x66, 0x78, 0x62, 0x71, 0x70, 0x48, 0x02, 0x05, 0x0D, 0x6C, 0x7F, 0x70, 0x1A, 0x31, 0x07, 0x05, 0x1F, 0x1F, 0xAE, 0xB9, 0xBA, 0xD3, 0xAC, 0x90, 0x93, 0x92, 0xEA, 0xA1, 0xA1, 0xAF, 0xA7, 0xB5, 0xCC, 0xDC, 0xC1, 0xCC, 0xD1, 0x37 };
const uint8_t enc_ttDesktopUa[111] = { 0x11, 0x0C, 0x10, 0x18, 0x14, 0x13, 0xE7, 0xA2, 0xA1, 0xB5, 0x92, 0x89, 0x98, 0xE0, 0xD7, 0xAB, 0xA8, 0xBC, 0xAD, 0x92, 0xC8, 0xA1, 0xA2, 0xDD, 0x35, 0x3B, 0x3C, 0x29, 0x1B, 0x07, 0x79, 0x5C, 0x52, 0x75, 0x7E, 0x6A, 0x78, 0x27, 0x50, 0x59, 0x5D, 0x5B, 0xC3, 0xF9, 0xE0, 0xFB, 0xFB, 0xF2, 0xC9, 0xD1, 0xF1, 0xA8, 0xBC, 0xE0, 0xE3, 0xEE, 0xD3, 0xC5, 0xC1, 0xCF, 0x20, 0x2F, 0x45, 0x5D, 0x48, 0x6E, 0x66, 0x1D, 0x18, 0x53, 0x2F, 0x26, 0x31, 0x7B, 0x25, 0x0C, 0x13, 0x1C, 0x11, 0xAC, 0xAC, 0xD0, 0xF2, 0xD3, 0xC7, 0xC2, 0xD3, 0x92, 0xF5, 0xF8, 0xE5, 0xF7, 0xD0, 0xC9, 0xDE, 0xDB, 0xCC, 0x23, 0x59, 0x70, 0x7E, 0x7E, 0x54, 0x44, 0x1B, 0x0E, 0x71, 0x7E, 0x7E, 0x64, 0x68 };
const uint8_t enc_ttAccept[85] = { 0x28, 0x06, 0x12, 0x05, 0x57, 0x17, 0xF2, 0xE0, 0xF8, 0xB7, 0xC3, 0xD9, 0xC0, 0xDB, 0xD7, 0xA6, 0xAD, 0xA7, 0xB3, 0x8E, 0x86, 0xC0, 0x8E, 0x95, 0x70, 0x66, 0x7E, 0x32, 0x58, 0x4A, 0x42, 0x19, 0x5D, 0x33, 0x3A, 0x3D, 0x31, 0x3C, 0x07, 0x19, 0x1D, 0x14, 0xEC, 0xA6, 0xE8, 0xFA, 0xF2, 0x9E, 0xDD, 0x8E, 0x8A, 0xEF, 0xF1, 0xE3, 0xBF, 0xB0, 0x85, 0x8C, 0x97, 0xD6, 0x61, 0x71, 0x67, 0x73, 0x30, 0x4A, 0x47, 0x50, 0x5F, 0x5A, 0x69, 0x3A, 0x31, 0x39, 0x12, 0x45, 0x5A, 0x58, 0x54, 0xBE, 0xFD, 0xAE, 0xAA, 0x8F, 0x90 };
const uint8_t enc_ttAcceptLang[35] = { 0x2A, 0x0A, 0x47, 0x27, 0x36, 0x53, 0xF0, 0xE4, 0xAF, 0xEA, 0x9F, 0x99, 0x9E, 0x8E, 0x92, 0xA0, 0xA2, 0xFE, 0x8F, 0xB2, 0xD3, 0x9E, 0xCB, 0xCD, 0x2A, 0x33, 0x3E, 0x7C, 0x4E, 0x1C, 0x5F, 0x08, 0x0C, 0x6D, 0x7D };
const uint8_t enc_ttSecFetchSite[4] = { 0x32, 0x0C, 0x04, 0x14 };
const uint8_t enc_ttSecFetchMode[8] = { 0x32, 0x02, 0x1C, 0x18, 0x1F, 0x1E, 0xF2, 0xE8 };
const uint8_t enc_ttDefaultScope[17] = { 0x03, 0x3C, 0x2E, 0x34, 0x3E, 0x3E, 0xD3, 0xC1, 0xC0, 0xC4, 0xF1, 0xEA, 0xFF, 0xE7, 0xFB, 0x9A, 0x93 };
const uint8_t enc_ttWebappUserDetail[18] = { 0x2B, 0x06, 0x08, 0x10, 0x08, 0x0F, 0xA8, 0xF8, 0xE7, 0xFE, 0xD0, 0x84, 0xD4, 0xD2, 0xCA, 0xA4, 0xA5, 0xBF };
const uint8_t enc_ttStatusCode[10] = { 0x2F, 0x17, 0x0B, 0x05, 0x0D, 0x0C, 0xC5, 0xE2, 0xF0, 0xFE };
const uint8_t enc_ttUserInfo[8] = { 0x29, 0x10, 0x0F, 0x03, 0x31, 0x11, 0xE0, 0xE2 };
const uint8_t enc_ttUser[4] = { 0x29, 0x10, 0x0F, 0x03 };
const uint8_t enc_ttStats[5] = { 0x2F, 0x17, 0x0B, 0x05, 0x0B };
const uint8_t enc_ttId[2] = { 0x35, 0x07 };
const uint8_t enc_ttUid[3] = { 0x29, 0x0A, 0x0E };
const uint8_t enc_ttSecUid[6] = { 0x2F, 0x06, 0x09, 0x24, 0x11, 0x1B };
const uint8_t enc_ttSecUidSnake[7] = { 0x2F, 0x06, 0x09, 0x2E, 0x0D, 0x16, 0xE2 };
const uint8_t enc_ttNickname[8] = { 0x32, 0x0A, 0x09, 0x1A, 0x16, 0x1E, 0xEB, 0xE8 };
const uint8_t enc_ttAvatarLarger[12] = { 0x3D, 0x15, 0x0B, 0x05, 0x19, 0x0D, 0xCA, 0xEC, 0xE6, 0xFC, 0xC7, 0xDB };
const uint8_t enc_ttAvatarMedium[12] = { 0x3D, 0x15, 0x0B, 0x05, 0x19, 0x0D, 0xCB, 0xE8, 0xF0, 0xF2, 0xD7, 0xC4 };
const uint8_t enc_ttAvatarThumb[11] = { 0x3D, 0x15, 0x0B, 0x05, 0x19, 0x0D, 0xD2, 0xE5, 0xE1, 0xF6, 0xC0 };
const uint8_t enc_ttSignature[9] = { 0x2F, 0x0A, 0x0D, 0x1F, 0x19, 0x0B, 0xF3, 0xFF, 0xF1 };
const uint8_t enc_ttPrivateAccount[14] = { 0x2C, 0x11, 0x03, 0x07, 0x19, 0x0B, 0xE3, 0xCC, 0xF7, 0xF8, 0xCD, 0xDC, 0xDE, 0xC3 };
const uint8_t enc_ttVerified[8] = { 0x2A, 0x06, 0x18, 0x18, 0x1E, 0x16, 0xE3, 0xE9 };
const uint8_t enc_ttCreateTime[10] = { 0x3F, 0x11, 0x0F, 0x10, 0x0C, 0x1A, 0xD2, 0xE4, 0xF9, 0xFE };
const uint8_t enc_ttUniqueId[8] = { 0x29, 0x0D, 0x03, 0x00, 0x0D, 0x1A, 0xCF, 0xE9 };
const uint8_t enc_ttFollowerCount[13] = { 0x3A, 0x0C, 0x06, 0x1D, 0x17, 0x08, 0xE3, 0xFF, 0xD7, 0xF4, 0xD7, 0xC7, 0xC4 };
const uint8_t enc_ttFollowingCount[14] = { 0x3A, 0x0C, 0x06, 0x1D, 0x17, 0x08, 0xEF, 0xE3, 0xF3, 0xD8, 0xCD, 0xDC, 0xDE, 0xC3 };
const uint8_t enc_ttHeartCount[10] = { 0x34, 0x06, 0x0B, 0x03, 0x0C, 0x3C, 0xE9, 0xF8, 0xFA, 0xEF };
const uint8_t enc_ttHeart[5] = { 0x34, 0x06, 0x0B, 0x03, 0x0C };
const uint8_t enc_ttVideoCount[10] = { 0x2A, 0x0A, 0x0E, 0x14, 0x17, 0x3C, 0xE9, 0xF8, 0xFA, 0xEF };
const uint8_t enc_ttUserModule[10] = { 0x09, 0x10, 0x0F, 0x03, 0x35, 0x10, 0xE2, 0xF8, 0xF8, 0xFE };
const uint8_t enc_ttUsers[5] = { 0x29, 0x10, 0x0F, 0x03, 0x0B };

static inline std::string getTtBaseUrl() { return decryptOllvmString(enc_ttBaseUrl, 22, 0x5C, 7); }
static inline std::string getTtTagUniversal() { return decryptOllvmString(enc_ttTagUniversal, 34, 0x5C, 7); }
static inline std::string getTtTagSigi() { return decryptOllvmString(enc_ttTagSigi, 10, 0x5C, 7); }
static inline std::string getTtMobileUa() { return decryptOllvmString(enc_ttMobileUa, 135, 0x5C, 7); }
static inline std::string getTtDesktopUa() { return decryptOllvmString(enc_ttDesktopUa, 111, 0x5C, 7); }
static inline std::string getTtAccept() { return decryptOllvmString(enc_ttAccept, 85, 0x5C, 7); }
static inline std::string getTtAcceptLang() { return decryptOllvmString(enc_ttAcceptLang, 35, 0x5C, 7); }
static inline std::string getTtSecFetchSite() { return decryptOllvmString(enc_ttSecFetchSite, 4, 0x5C, 7); }
static inline std::string getTtSecFetchMode() { return decryptOllvmString(enc_ttSecFetchMode, 8, 0x5C, 7); }

static inline uint64_t calculateSnowflakeTimestamp(const std::string& uidStr) {
    if (uidStr.empty()) return 0;
    char* endPtr = nullptr;
    unsigned long long uid = strtoull(uidStr.c_str(), &endPtr, 10);
    if (uid == 0) return 0;
    uint64_t ts = (uint64_t)(uid >> 32);
    if (ts >= 1400000000ULL && ts <= 2500000000ULL) {
        return ts;
    }
    return 0;
}

static inline std::string escapeJsonString(const std::string& input) {
    std::ostringstream ss;
    for (char c : input) {
        switch (c) {
            case '"': ss << "\""; break;
            case '\\': ss << "\\\\"; break;
            case '\b': ss << "\\b"; break;
            case '\f': ss << "\\f"; break;
            case '\n': ss << "\\n"; break;
            case '\r': ss << "\\r"; break;
            case '\t': ss << "\\t"; break;
            default:
                if ((unsigned char)c < 0x20) {
                    char buf[7];
                    snprintf(buf, sizeof(buf), "\\u%04x", (unsigned char)c);
                    ss << buf;
                } else {
                    ss << c;
                }
                break;
        }
    }
    return ss.str();
}

static inline std::string extractScriptContent(const std::string& html, const std::string& tagId) {
    if (html.empty() || tagId.empty()) return "";
    std::string needle1 = "<script id=\"" + tagId + "\"";
    size_t pos = html.find(needle1);
    if (pos == std::string::npos) {
        std::string needle2 = "<script id='" + tagId + "'";
        pos = html.find(needle2);
    }
    if (pos == std::string::npos) {
        std::string needle3 = "id=\"" + tagId + "\"";
        pos = html.find(needle3);
        if (pos != std::string::npos) {
            size_t scriptTag = html.rfind("<script", pos);
            if (scriptTag != std::string::npos && pos - scriptTag < 100) {
                pos = scriptTag;
            } else {
                pos = std::string::npos;
            }
        }
    }
    if (pos == std::string::npos) return "";

    size_t tagEnd = html.find('>', pos);
    if (tagEnd == std::string::npos) return "";

    size_t closeTag = html.find("</script>", tagEnd + 1);
    if (closeTag == std::string::npos) return "";

    size_t start = tagEnd + 1;
    if (closeTag <= start) return "";

    return html.substr(start, closeTag - start);
}

}

static jstring native_sqlite_sec(JNIEnv *env, jclass clazz) {
    std::string secret = getSqliteSecret();
    return env->NewStringUTF(secret.c_str());
}

static jstring native_fb_oauth(JNIEnv *env, jclass clazz) {
    std::string token = getFbOAuthToken();
    return env->NewStringUTF(token.c_str());
}

static jstring native_fb_app_token(JNIEnv *env, jclass clazz) {
    std::string token = getFbAppToken();
    return env->NewStringUTF(token.c_str());
}

static jstring native_fb_api_key(JNIEnv *env, jclass clazz) {
    std::string key = getFbApiKey();
    return env->NewStringUTF(key.c_str());
}

static jstring native_fb_sig(JNIEnv *env, jclass clazz) {
    std::string sig = getFbSig();
    return env->NewStringUTF(sig.c_str());
}

static jstring native_fb_key_fetch(JNIEnv *env, jclass clazz) {
    std::string k = getFbKeyFetchToken();
    return env->NewStringUTF(k.c_str());
}

static jstring native_fb_docid(JNIEnv *env, jclass clazz) {
    std::string docId = getFbBloksDocId();
    return env->NewStringUTF(docId.c_str());
}

static jstring native_fb_ua(JNIEnv *env, jclass clazz) {
    std::string ua = getFbKatanaUA();
    return env->NewStringUTF(ua.c_str());
}

static jstring native_fb_dalvik_ua(JNIEnv *env, jclass clazz) {
    std::string ua = getFbDalvikUA();
    return env->NewStringUTF(ua.c_str());
}


static jstring native_tt_base_url(JNIEnv *env, jclass clazz) {
    std::string url = getTtBaseUrl();
    return env->NewStringUTF(url.c_str());
}

static jstring native_tt_mobile_ua(JNIEnv *env, jclass clazz) {
    std::string ua = getTtMobileUa();
    return env->NewStringUTF(ua.c_str());
}

static jstring native_tt_desktop_ua(JNIEnv *env, jclass clazz) {
    std::string ua = getTtDesktopUa();
    return env->NewStringUTF(ua.c_str());
}

static jstring native_tt_accept(JNIEnv *env, jclass clazz) {
    std::string acc = getTtAccept();
    return env->NewStringUTF(acc.c_str());
}

static jstring native_tt_accept_lang(JNIEnv *env, jclass clazz) {
    std::string lang = getTtAcceptLang();
    return env->NewStringUTF(lang.c_str());
}

static jstring native_tt_sec_fetch_site(JNIEnv *env, jclass clazz) {
    std::string site = getTtSecFetchSite();
    return env->NewStringUTF(site.c_str());
}

static jstring native_tt_sec_fetch_mode(JNIEnv *env, jclass clazz) {
    std::string mode = getTtSecFetchMode();
    return env->NewStringUTF(mode.c_str());
}

static jlong native_tt_snowflake(JNIEnv *env, jclass clazz, jstring uidStr) {
    if (!uidStr) return 0;
    const char *chars = env->GetStringUTFChars(uidStr, nullptr);
    if (!chars) return 0;
    std::string s(chars);
    env->ReleaseStringUTFChars(uidStr, chars);
    return (jlong)calculateSnowflakeTimestamp(s);
}

static jstring native_tt_extract_profile(JNIEnv *env, jclass clazz, jstring htmlStr, jstring userStr) {
    if (!htmlStr || !userStr) return nullptr;
    const char *htmlChars = env->GetStringUTFChars(htmlStr, nullptr);
    const char *userChars = env->GetStringUTFChars(userStr, nullptr);
    if (!htmlChars || !userChars) {
        if (htmlChars) env->ReleaseStringUTFChars(htmlStr, htmlChars);
        if (userChars) env->ReleaseStringUTFChars(userStr, userChars);
        return nullptr;
    }
    std::string html(htmlChars);
    std::string cleanUser(userChars);
    env->ReleaseStringUTFChars(htmlStr, htmlChars);
    env->ReleaseStringUTFChars(userStr, userChars);

    bool isUniversal = true;
    std::string scriptJson = extractScriptContent(html, getTtTagUniversal());
    if (scriptJson.empty()) {
        isUniversal = false;
        scriptJson = extractScriptContent(html, getTtTagSigi());
    }
    if (scriptJson.empty()) {
        return nullptr;
    }

    jclass jsonClass = env->FindClass("org/json/JSONObject");
    if (!jsonClass) return nullptr;
    jmethodID jsonInit = env->GetMethodID(jsonClass, "<init>", "(Ljava/lang/String;)V");
    jmethodID optObjMethod = env->GetMethodID(jsonClass, "optJSONObject", "(Ljava/lang/String;)Lorg/json/JSONObject;");
    jmethodID optStringMethod = env->GetMethodID(jsonClass, "optString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    jmethodID optLongMethod = env->GetMethodID(jsonClass, "optLong", "(Ljava/lang/String;J)J");
    jmethodID optIntMethod = env->GetMethodID(jsonClass, "optInt", "(Ljava/lang/String;I)I");
    jmethodID optBoolMethod = env->GetMethodID(jsonClass, "optBoolean", "(Ljava/lang/String;Z)Z");

    if (!jsonInit || !optObjMethod || !optStringMethod || !optLongMethod || !optIntMethod || !optBoolMethod) {
        return nullptr;
    }

    jstring jRawJson = env->NewStringUTF(scriptJson.c_str());
    if (!jRawJson) return nullptr;
    jobject rootObj = env->NewObject(jsonClass, jsonInit, jRawJson);
    env->DeleteLocalRef(jRawJson);
    if (env->ExceptionCheck() || !rootObj) {
        env->ExceptionClear();
        return nullptr;
    }

    auto getOptObj = [&](jobject obj, const std::string& key) -> jobject {
        if (!obj) return nullptr;
        jstring jKey = env->NewStringUTF(key.c_str());
        jobject res = env->CallObjectMethod(obj, optObjMethod, jKey);
        env->DeleteLocalRef(jKey);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return nullptr; }
        return res;
    };

    auto getOptString = [&](jobject obj, const std::string& key, const std::string& dflt) -> std::string {
        if (!obj) return dflt;
        jstring jKey = env->NewStringUTF(key.c_str());
        jstring jDflt = env->NewStringUTF(dflt.c_str());
        jstring jRes = (jstring)env->CallObjectMethod(obj, optStringMethod, jKey, jDflt);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jDflt);
        if (env->ExceptionCheck() || !jRes) { env->ExceptionClear(); return dflt; }
        const char *chars = env->GetStringUTFChars(jRes, nullptr);
        std::string val = chars ? chars : dflt;
        if (chars) env->ReleaseStringUTFChars(jRes, chars);
        env->DeleteLocalRef(jRes);
        return val;
    };

    auto getOptLong = [&](jobject obj, const std::string& key, jlong dflt) -> jlong {
        if (!obj) return dflt;
        jstring jKey = env->NewStringUTF(key.c_str());
        jlong res = env->CallLongMethod(obj, optLongMethod, jKey, dflt);
        env->DeleteLocalRef(jKey);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return dflt; }
        return res;
    };

    auto getOptInt = [&](jobject obj, const std::string& key, jint dflt) -> jint {
        if (!obj) return dflt;
        jstring jKey = env->NewStringUTF(key.c_str());
        jint res = env->CallIntMethod(obj, optIntMethod, jKey, dflt);
        env->DeleteLocalRef(jKey);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return dflt; }
        return res;
    };

    auto getOptBool = [&](jobject obj, const std::string& key, jboolean dflt) -> jboolean {
        if (!obj) return dflt;
        jstring jKey = env->NewStringUTF(key.c_str());
        jboolean res = env->CallBooleanMethod(obj, optBoolMethod, jKey, dflt);
        env->DeleteLocalRef(jKey);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return dflt; }
        return res;
    };

    if (isUniversal) {
        jobject defaultScope = getOptObj(rootObj, decryptOllvmString(enc_ttDefaultScope, 17, 0x5C, 7));
        jobject userDetail = getOptObj(defaultScope, decryptOllvmString(enc_ttWebappUserDetail, 18, 0x5C, 7));
        if (userDetail) {
            int statusCode = getOptInt(userDetail, decryptOllvmString(enc_ttStatusCode, 10, 0x5C, 7), 0);
            if (statusCode == 10221) {
                std::string notFoundJson = "{\"userId\":\"\",\"username\":\"" + escapeJsonString(cleanUser) + "\",\"nickname\":\"" + escapeJsonString(cleanUser) + "\",\"isLive\":false}";
                return env->NewStringUTF(notFoundJson.c_str());
            }
            jobject userInfo = getOptObj(userDetail, decryptOllvmString(enc_ttUserInfo, 8, 0x5C, 7));
            jobject userJson = getOptObj(userInfo, decryptOllvmString(enc_ttUser, 4, 0x5C, 7));
            jobject statsJson = getOptObj(userInfo, decryptOllvmString(enc_ttStats, 5, 0x5C, 7));
            if (userJson) {
                std::string userId = getOptString(userJson, decryptOllvmString(enc_ttId, 2, 0x5C, 7), "");
                if (userId.empty()) {
                    userId = getOptString(userJson, decryptOllvmString(enc_ttUid, 3, 0x5C, 7), "");
                }
                if (!userId.empty()) {
                    std::string secUid = getOptString(userJson, decryptOllvmString(enc_ttSecUid, 6, 0x5C, 7), "");
                    if (secUid.empty()) {
                        secUid = getOptString(userJson, decryptOllvmString(enc_ttSecUidSnake, 7, 0x5C, 7), "");
                    }
                    std::string nickname = getOptString(userJson, decryptOllvmString(enc_ttNickname, 8, 0x5C, 7), cleanUser);
                    std::string avatarLarger = getOptString(userJson, decryptOllvmString(enc_ttAvatarLarger, 12, 0x5C, 7), "");
                    std::string avatarMedium = getOptString(userJson, decryptOllvmString(enc_ttAvatarMedium, 12, 0x5C, 7), "");
                    std::string avatarThumb = getOptString(userJson, decryptOllvmString(enc_ttAvatarThumb, 11, 0x5C, 7), "");
                    std::string finalAvatar = avatarLarger.empty() ? avatarMedium : avatarLarger;
                    std::string bio = getOptString(userJson, decryptOllvmString(enc_ttSignature, 9, 0x5C, 7), "");
                    bool isPrivate = (bool)getOptBool(userJson, decryptOllvmString(enc_ttPrivateAccount, 14, 0x5C, 7), false);
                    bool isVerified = (bool)getOptBool(userJson, decryptOllvmString(enc_ttVerified, 8, 0x5C, 7), false);
                    int64_t createTime = (int64_t)getOptLong(userJson, decryptOllvmString(enc_ttCreateTime, 10, 0x5C, 7), 0);
                    if (createTime <= 0) {
                        createTime = (int64_t)calculateSnowflakeTimestamp(userId);
                    }
                    std::string officialUsername = getOptString(userJson, decryptOllvmString(enc_ttUniqueId, 8, 0x5C, 7), cleanUser);
                    if (officialUsername.empty()) officialUsername = cleanUser;

                    int64_t followers = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttFollowerCount, 13, 0x5C, 7), 0);
                    int64_t following = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttFollowingCount, 14, 0x5C, 7), 0);
                    int64_t hearts = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttHeartCount, 10, 0x5C, 7), 0);
                    if (hearts == 0) {
                        hearts = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttHeart, 5, 0x5C, 7), 0);
                    }
                    int64_t videoCount = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttVideoCount, 10, 0x5C, 7), 0);

                    std::ostringstream ss;
                    ss << "{"
                       << "\"userId\":\"" << escapeJsonString(userId) << "\","
                       << "\"secUid\":\"" << escapeJsonString(secUid) << "\","
                       << "\"username\":\"" << escapeJsonString(officialUsername) << "\","
                       << "\"nickname\":\"" << escapeJsonString(nickname) << "\","
                       << "\"avatarHdUrl\":\"" << escapeJsonString(finalAvatar) << "\","
                       << "\"avatarThumbUrl\":\"" << escapeJsonString(avatarThumb) << "\","
                       << "\"biography\":\"" << escapeJsonString(bio) << "\","
                       << "\"followerCount\":" << followers << ","
                       << "\"followingCount\":" << following << ","
                       << "\"totalFavorited\":" << hearts << ","
                       << "\"videoCount\":" << videoCount << ","
                       << "\"isPrivate\":" << (isPrivate ? "true" : "false") << ","
                       << "\"isVerified\":" << (isVerified ? "true" : "false") << ","
                       << "\"createTimestampSec\":" << createTime << ","
                       << "\"isLive\":true"
                       << "}";
                    return env->NewStringUTF(ss.str().c_str());
                }
            }
        }
    } else {
        jobject userModule = getOptObj(rootObj, decryptOllvmString(enc_ttUserModule, 10, 0x5C, 7));
        if (userModule) {
            jobject usersObj = getOptObj(userModule, decryptOllvmString(enc_ttUsers, 5, 0x5C, 7));
            jobject statsModule = getOptObj(userModule, decryptOllvmString(enc_ttStats, 5, 0x5C, 7));
            jobject userJson = getOptObj(usersObj, cleanUser);
            jobject statsJson = getOptObj(statsModule, cleanUser);
            if (userJson) {
                std::string userId = getOptString(userJson, decryptOllvmString(enc_ttId, 2, 0x5C, 7), "");
                if (!userId.empty()) {
                    std::string secUid = getOptString(userJson, decryptOllvmString(enc_ttSecUid, 6, 0x5C, 7), "");
                    std::string nickname = getOptString(userJson, decryptOllvmString(enc_ttNickname, 8, 0x5C, 7), cleanUser);
                    std::string avatarLarger = getOptString(userJson, decryptOllvmString(enc_ttAvatarLarger, 12, 0x5C, 7), "");
                    std::string avatarMedium = getOptString(userJson, decryptOllvmString(enc_ttAvatarMedium, 12, 0x5C, 7), "");
                    std::string avatarThumb = getOptString(userJson, decryptOllvmString(enc_ttAvatarThumb, 11, 0x5C, 7), "");
                    std::string finalAvatar = avatarLarger.empty() ? avatarMedium : avatarLarger;
                    std::string bio = getOptString(userJson, decryptOllvmString(enc_ttSignature, 9, 0x5C, 7), "");
                    bool isPrivate = (bool)getOptBool(userJson, decryptOllvmString(enc_ttPrivateAccount, 14, 0x5C, 7), false);
                    bool isVerified = (bool)getOptBool(userJson, decryptOllvmString(enc_ttVerified, 8, 0x5C, 7), false);
                    int64_t createTime = (int64_t)getOptLong(userJson, decryptOllvmString(enc_ttCreateTime, 10, 0x5C, 7), 0);
                    if (createTime <= 0) {
                        createTime = (int64_t)calculateSnowflakeTimestamp(userId);
                    }
                    std::string officialUsername = getOptString(userJson, decryptOllvmString(enc_ttUniqueId, 8, 0x5C, 7), cleanUser);
                    if (officialUsername.empty()) officialUsername = cleanUser;

                    int64_t followers = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttFollowerCount, 13, 0x5C, 7), 0);
                    int64_t following = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttFollowingCount, 14, 0x5C, 7), 0);
                    int64_t hearts = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttHeartCount, 10, 0x5C, 7), 0);
                    int64_t videoCount = (int64_t)getOptLong(statsJson, decryptOllvmString(enc_ttVideoCount, 10, 0x5C, 7), 0);

                    std::ostringstream ss;
                    ss << "{"
                       << "\"userId\":\"" << escapeJsonString(userId) << "\","
                       << "\"secUid\":\"" << escapeJsonString(secUid) << "\","
                       << "\"username\":\"" << escapeJsonString(officialUsername) << "\","
                       << "\"nickname\":\"" << escapeJsonString(nickname) << "\","
                       << "\"avatarHdUrl\":\"" << escapeJsonString(finalAvatar) << "\","
                       << "\"avatarThumbUrl\":\"" << escapeJsonString(avatarThumb) << "\","
                       << "\"biography\":\"" << escapeJsonString(bio) << "\","
                       << "\"followerCount\":" << followers << ","
                       << "\"followingCount\":" << following << ","
                       << "\"totalFavorited\":" << hearts << ","
                       << "\"videoCount\":" << videoCount << ","
                       << "\"isPrivate\":" << (isPrivate ? "true" : "false") << ","
                       << "\"isVerified\":" << (isVerified ? "true" : "false") << ","
                       << "\"createTimestampSec\":" << createTime << ","
                       << "\"isLive\":true"
                       << "}";
                    return env->NewStringUTF(ss.str().c_str());
                }
            }
        }
    }

    return nullptr;
}

static const JNINativeMethod gSqliteMethods[] = {
    { (char*)"_sqliteSec", (char*)"()Ljava/lang/String;", (void*)native_sqlite_sec },
    { (char*)"_fbOAuth", (char*)"()Ljava/lang/String;", (void*)native_fb_oauth },
    { (char*)"_fbAppToken", (char*)"()Ljava/lang/String;", (void*)native_fb_app_token },
    { (char*)"_fbApiKey", (char*)"()Ljava/lang/String;", (void*)native_fb_api_key },
    { (char*)"_fbSig", (char*)"()Ljava/lang/String;", (void*)native_fb_sig },
    { (char*)"_fbKeyFetch", (char*)"()Ljava/lang/String;", (void*)native_fb_key_fetch },
    { (char*)"_fbDocId", (char*)"()Ljava/lang/String;", (void*)native_fb_docid },
    { (char*)"_fbUa", (char*)"()Ljava/lang/String;", (void*)native_fb_ua },
        { (char*)"_fbDalvikUa", (char*)"()Ljava/lang/String;", (void*)native_fb_dalvik_ua },
    // TikTok Hardened Security APIs
    { (char*)"_ttBaseUrl", (char*)"()Ljava/lang/String;", (void*)native_tt_base_url },
    { (char*)"_ttMobileUa", (char*)"()Ljava/lang/String;", (void*)native_tt_mobile_ua },
    { (char*)"_ttDesktopUa", (char*)"()Ljava/lang/String;", (void*)native_tt_desktop_ua },
    { (char*)"_ttAccept", (char*)"()Ljava/lang/String;", (void*)native_tt_accept },
    { (char*)"_ttAcceptLang", (char*)"()Ljava/lang/String;", (void*)native_tt_accept_lang },
    { (char*)"_ttSecFetchSite", (char*)"()Ljava/lang/String;", (void*)native_tt_sec_fetch_site },
    { (char*)"_ttSecFetchMode", (char*)"()Ljava/lang/String;", (void*)native_tt_sec_fetch_mode },
    { (char*)"_ttSnowflake", (char*)"(Ljava/lang/String;)J", (void*)native_tt_snowflake },
    { (char*)"_ttExtractProfile", (char*)"(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", (void*)native_tt_extract_profile }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/cayxu/app/util/NativeSecurity");
    if (!clazz) return JNI_ERR;

    if (env->RegisterNatives(clazz, gSqliteMethods, sizeof(gSqliteMethods) / sizeof(gSqliteMethods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}