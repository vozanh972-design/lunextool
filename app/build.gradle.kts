plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ============================================================================
// Massive Multi-Language Random Obfuscation Dictionary Generator (250,000+ Unique Tokens)
// Tự động random 1 trong 15 ngôn ngữ chuẩn quốc gia:
// 1. Trung Quốc, 2. Ả Rập Xê Út, 3. Nhật Bản, 4. Hàn Quốc, 5. Hungary,
// 6. Phần Lan, 7. Nga, 8. Iceland, 9. Thái Lan, 10. Ba Lan,
// 11. Thổ Nhĩ Kỳ, 12. Hy Lạp, 13. Đức, 14. Mông Cổ, 15. Đan Mạch
// ============================================================================
enum class ObfuscationLanguage(val displayName: String, val charRanges: List<IntRange>) {
    CHINESE(
        "1. Trung Quốc (Chinese CJK Unified Ideographs 20,000+ Characters)",
        listOf(
            0x4E00..0x9FFF,
            0x3400..0x4DBF
        )
    ),
    SAUDI_ARABIA(
        "2. Ả Rập Xê Út (Arabic Full Unicode & Presentation Forms)",
        listOf(
            0x0621..0x064A,
            0x066E..0x06D3,
            0x0750..0x077F,
            0x08A0..0x08FF,
            0xFB50..0xFD8F,
            0xFE70..0xFEFC
        )
    ),
    JAPAN(
        "3. Nhật Bản (Japanese Hiragana, Katakana & Kanji)",
        listOf(
            0x3041..0x3096,
            0x30A1..0x30FA,
            0x4E00..0x7FFF
        )
    ),
    KOREA(
        "4. Hàn Quốc (Korean Hangul Syllables & Jamo)",
        listOf(
            0xAC00..0xD7AF,
            0x1100..0x11FF,
            0x3130..0x318F
        )
    ),
    HUNGARY(
        "5. Hungary (Hungarian Latin Extended & Accented Letters)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x1E00..0x1EFF
        )
    ),
    FINLAND(
        "6. Phần Lan (Finnish Nordic Latin Extended)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x2C60..0x2C7F
        )
    ),
    RUSSIA(
        "7. Nga (Russian / Cyrillic Extended)",
        listOf(
            0x0400..0x04FF,
            0x0500..0x052F,
            0x2DE0..0x2DFF,
            0xA640..0xA69F,
            0x1C80..0x1C88
        )
    ),
    ICELAND(
        "8. Iceland (Icelandic Eth, Thorn, Ash & Extended Runes/Latin)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x1E00..0x1EFF,
            0x2C60..0x2C7F
        )
    ),
    THAILAND(
        "9. Thái Lan (Thai Script & Extended)",
        listOf(
            0x0E01..0x0E3A,
            0x0E40..0x0E4E
        )
    ),
    POLAND(
        "10. Ba Lan (Polish Ogonek, Kreska, Kropka & Latin Extended)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x1E00..0x1EFF
        )
    ),
    TURKEY(
        "11. Thổ Nhĩ Kỳ (Turkish Cedilla, Breve, Dotless I & Latin Extended)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x1E00..0x1EFF
        )
    ),
    GREECE(
        "12. Hy Lạp (Greek & Greek Extended)",
        listOf(
            0x0370..0x03FF,
            0x1F00..0x1FFF
        )
    ),
    GERMANY(
        "13. Đức (German Umlauts, Eszett & Latin Extended-B)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x1E00..0x1EFF
        )
    ),
    MONGOLIA(
        "14. Mông Cổ (Mongolian Traditional Script & Cyrillic Mongolia)",
        listOf(
            0x1800..0x18AF,
            0x0400..0x04FF,
            0x0500..0x052F
        )
    ),
    DENMARK(
        "15. Đan Mạch (Danish Nordic Latin Extended, AE, O-slash, A-ring)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x2C60..0x2C7F
        )
    )
}

fun generateRandomDictionary(targetFiles: List<File>) {
    val selectedLang = ObfuscationLanguage.values().random()
    println("🔒 [ProGuard/R8 Hardening] Đang áp dụng từ điển ngẫu nhiên: ${selectedLang.displayName}")

    val startChars = selectedLang.charRanges.flatMap { range ->
        range.map { it.toChar() }
    }.filter { Character.isJavaIdentifierStart(it) }.distinct()

    val partChars = selectedLang.charRanges.flatMap { range ->
        range.map { it.toChar() }
    }.filter { Character.isJavaIdentifierPart(it) }.distinct()

    val safeStarts = if (startChars.isNotEmpty()) startChars else ('a'..'z').toList()
    val safeParts = if (partChars.isNotEmpty()) partChars else safeStarts

    val tokens = LinkedHashSet<String>()

    // 1. Thêm tất cả ký tự đơn hợp lệ
    safeStarts.forEach { tokens.add(it.toString()) }

    val TARGET_TOKENS = 250000

    // 2. Tổ hợp 2 ký tự (start + part)
    val shuffledStarts = safeStarts.shuffled()
    val shuffledParts = safeParts.shuffled()
    for (c1 in shuffledStarts) {
        for (c2 in shuffledParts) {
            tokens.add("$c1$c2")
            if (tokens.size >= TARGET_TOKENS) break
        }
        if (tokens.size >= TARGET_TOKENS) break
    }

    // 3. Tổ hợp 3 ký tự (bao trùm toàn bộ classes.dex, classes2.dex, classes3.dex...)
    if (tokens.size < TARGET_TOKENS) {
        val pool1 = if (shuffledStarts.size > 350) shuffledStarts.take(350) else shuffledStarts
        val pool2 = if (shuffledParts.size > 350) shuffledParts.take(350) else shuffledParts
        for (c1 in pool1) {
            for (c2 in pool2) {
                for (c3 in pool2) {
                    tokens.add("$c1$c2$c3")
                    if (tokens.size >= TARGET_TOKENS) break
                }
                if (tokens.size >= TARGET_TOKENS) break
            }
            if (tokens.size >= TARGET_TOKENS) break
        }
    }

    // 4. Tổ hợp 4 ký tự (dự phòng tối đa nếu cần đạt mốc khổng lồ)
    if (tokens.size < TARGET_TOKENS) {
        val pool1 = if (shuffledStarts.size > 80) shuffledStarts.take(80) else shuffledStarts
        val pool2 = if (shuffledParts.size > 80) shuffledParts.take(80) else shuffledParts
        for (c1 in pool1) {
            for (c2 in pool2) {
                for (c3 in pool2) {
                    for (c4 in pool2) {
                        tokens.add("$c1$c2$c3$c4")
                        if (tokens.size >= TARGET_TOKENS) break
                    }
                    if (tokens.size >= TARGET_TOKENS) break
                }
                if (tokens.size >= TARGET_TOKENS) break
            }
            if (tokens.size >= TARGET_TOKENS) break
        }
    }

    val content = tokens.shuffled().joinToString("\n")
    targetFiles.forEach { file ->
        try {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {}
    }
    println("🔒 [ProGuard/R8 Hardening] Đã sinh thành công ${tokens.size} tokens từ điển ${selectedLang.displayName}")
}

// ============================================================================
// Randomized Native Decoy .so Module Generator (15 đến 30 .so ngẫu nhiên mỗi lần build)
// Tạo ngẫu nhiên 15 - 30 file .so rác với tên ngẫu nhiên chuẩn format native (lib*.so)
// để đánh lừa toàn diện các công cụ phân tích tệp nhị phân và dịch ngược (IDA, Ghidra, APKTool)
// ============================================================================
fun generateRandomDecoySoModules(cppDir: File) {
    val prefixes = listOf("fb", "meta", "tiktok", "breakpad", "superpack", "dextricks", "achilles", "profiler", "v8", "hermes", "folly", "flipper", "yoga", "cryptox", "secguard", "syshook", "art_opt", "dex_opt", "turbo", "xlog", "jni_helper", "distract", "shadow", "core_tracer", "libunwind", "dexmaker", "av_pipeline", "hybrid_bridge", "fbsig", "quic_engine")
    val suffixes = listOf("jni", "core", "impl", "early", "late", "helper", "loader", "bridge", "native", "client", "tracer", "runtime", "engine", "parser", "transport", "v2", "opt", "sec", "stream", "filter")

    val totalTarget = (15..30).random()
    val coreCount = 4 // 4 module thật: androidx.graphics.path, distract-config, sqlitejni, imagepipeline
    val decoyCount = (totalTarget - coreCount).coerceAtLeast(11)
    val chosenNames = mutableSetOf<String>()

    while (chosenNames.size < decoyCount) {
        val p = prefixes.random()
        val s = suffixes.random()
        val num = if ((0..1).random() == 1) "_${(1..99).random()}" else ""
        val name = "${p}_${s}${num}".replace("__", "_")
        chosenNames.add(name)
    }

    val cmakeFile = File(cppDir, "CMakeLists.txt")
    val cmakeContent = StringBuilder()
    cmakeContent.append("""
cmake_minimum_required(VERSION 3.22.1)

project("app_native_bundle")

find_library(
    log-lib
    log
)

set(COMMON_COMPILE_OPTIONS
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fomit-frame-pointer
    -fdata-sections
    -ffunction-sections
    -fno-rtti
    -fno-exceptions
)

set(COMMON_LINK_OPTIONS
    -Wl,--gc-sections
    -Wl,--strip-all
    -Wl,--exclude-libs,ALL
)

macro(add_so_module TARGET_NAME SOURCE_FILE)
    add_library(${'$'}{TARGET_NAME} SHARED ${'$'}{SOURCE_FILE})
    target_compile_options(${'$'}{TARGET_NAME} PRIVATE ${'$'}{COMMON_COMPILE_OPTIONS})
    target_link_options(${'$'}{TARGET_NAME} PRIVATE ${'$'}{COMMON_LINK_OPTIONS})
    target_link_libraries(${'$'}{TARGET_NAME} ${'$'}{log-lib})
endmacro()

# 1. Các module phân mảnh cốt lõi (Core Security & Graphics & Crypto)
add_so_module(androidx.graphics.path androidx_graphics_path.cpp)
add_so_module(distract-config distract_config.cpp)
add_so_module(sqlitejni sqlitejni.cpp)
add_so_module(imagepipeline imagepipeline.cpp)

# 2. Các module mồi nhử ngẫu nhiên (${chosenNames.size} .so ngẫu nhiên cho lần build này)
""".trimIndent()).append("\n")

    chosenNames.forEach { soName ->
        cmakeContent.append("add_so_module($soName decoys.cpp)\n")
    }

    cmakeFile.writeText(cmakeContent.toString(), Charsets.UTF_8)
    println("🔒 [Native Hardening] Đã sinh ngẫu nhiên ${chosenNames.size} module .so mồi nhử cho lần build này.")
}

// ============================================================================
// Randomized Smali Bytecode Inliner & Control Flow Optimization Generator
// Tự động random số vòng tối ưu hóa (5-10 passes) và đảo ngẫu nhiên thứ tự các token
// trong từ điển để mỗi lần build mã Smali Opcode được xào theo ma trận hoàn toàn mới
// ============================================================================
fun updateRandomBytecodeRules(rulesFile: File) {
    if (!rulesFile.exists()) return
    val randomPasses = (6..10).random()
    var content = rulesFile.readText(Charsets.UTF_8)
    content = content.replace(Regex("-optimizationpasses \\d+"), "-optimizationpasses $randomPasses")
    rulesFile.writeText(content, Charsets.UTF_8)
    println("🔒 [Bytecode Hardening] Đã kích hoạt ma trận xào Smali ngẫu nhiên ($randomPasses chu kỳ tối ưu hóa)")
}

// Tự động sinh từ điển ngẫu nhiên, danh sách .so mồi nhử và cấu hình xào Smali khi cấu hình / build
generateRandomDictionary(listOf(file("dict_random.txt"), rootProject.file("dict_random.txt")))
generateRandomDecoySoModules(file("src/main/cpp"))
updateRandomBytecodeRules(file("proguard-rules.pro"))
updateRandomBytecodeRules(rootProject.file("proguard-rules.pro"))

android {
    namespace = "com.cayxu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cayxu.app"
        // Android 7.0 (Nougat) trở lên
        minSdk = 24
        // targetSdk sẽ được nâng lên khi Android 16 SDK chính thức phát hành
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        externalNativeBuild {
            cmake {
                cppFlags.addAll(listOf("-O3", "-fvisibility=hidden", "-fvisibility-inlines-hidden"))
            }
        }
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Chỉ tạo signingConfig "release" khi có đủ thông tin keystore
    // (lấy từ biến môi trường khi build trên GitHub Actions, hoặc từ
    // local.properties khi build tay trên máy). Nếu thiếu, APK release
    // vẫn build được nhưng sẽ ký bằng debug key (không dùng để phát hành).
    val keystorePath = System.getenv("KEYSTORE_FILE") ?: "release.keystore"
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPasswordEnv = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = file(keystorePath).exists() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasEnv.isNullOrBlank() &&
        !keyPasswordEnv.isNullOrBlank()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Secure local storage cho key đăng nhập
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager: dùng để lên lịch tự động gọi lại verify_key.php định kỳ
    // (ngẫu nhiên 3-10 tiếng/lần), phát hiện key bị thu hồi/hết hạn ngay cả khi
    // app đã bị patch để bypass màn Login lúc mở app.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coil để load avatar
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
