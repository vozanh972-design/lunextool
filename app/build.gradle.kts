plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ============================================================================
// Massive Multi-Language Random Obfuscation Dictionary Generator (50,000+ Unique Tokens)
// Tự động random 1 ngôn ngữ độc nhất (Ả Rập, Nga, Trung Quốc, Thái Lan, Thổ Nhĩ Kỳ, Hy Lạp, Nhật Bản)
// với toàn bộ dải Unicode đầy đủ (hàng chục nghìn ký tự & tổ hợp biến thể) mỗi lần build
// ============================================================================
enum class ObfuscationLanguage(val displayName: String, val charRanges: List<IntRange>) {
    ARABIC(
        "Tiếng Ả Rập (Arabic Full Unicode & Presentation Forms)",
        listOf(
            0x0621..0x064A,
            0x066E..0x06D3,
            0x0750..0x077F,
            0x08A0..0x08FF,
            0xFB50..0xFD8F,
            0xFE70..0xFEFC
        )
    ),
    RUSSIAN(
        "Tiếng Nga (Russian / Cyrillic Extended)",
        listOf(
            0x0400..0x04FF,
            0x0500..0x052F,
            0x2DE0..0x2DFF,
            0xA640..0xA69F,
            0x1C80..0x1C88
        )
    ),
    CHINESE(
        "Tiếng Trung (Chinese CJK Unified Ideographs 20,000+ Characters)",
        listOf(
            0x4E00..0x9FFF,
            0x3400..0x4DBF
        )
    ),
    THAI(
        "Tiếng Thái (Thai Script & Extended)",
        listOf(
            0x0E01..0x0E3A,
            0x0E40..0x0E4E
        )
    ),
    TURKISH(
        "Tiếng Thổ Nhĩ Kỳ (Turkish / Latin Extended Additional)",
        listOf(
            0x00C0..0x00FF,
            0x0100..0x017F,
            0x0180..0x024F,
            0x1E00..0x1EFF
        )
    ),
    GREEK(
        "Tiếng Hy Lạp (Greek & Greek Extended)",
        listOf(
            0x0370..0x03FF,
            0x1F00..0x1FFF
        )
    ),
    JAPANESE(
        "Tiếng Nhật (Japanese Hiragana, Katakana & Kanji)",
        listOf(
            0x3041..0x3096,
            0x30A1..0x30FA,
            0x4E00..0x7FFF
        )
    )
}

fun generateRandomDictionary(targetFiles: List<File>) {
    val selectedLang = ObfuscationLanguage.values().random()
    println("🔒 [ProGuard/R8 Hardening] Đang áp dụng từ điển ngẫu nhiên: ${selectedLang.displayName}")

    val allChars = selectedLang.charRanges.flatMap { range ->
        range.map { it.toChar() }
    }.filter { Character.isJavaIdentifierStart(it) }.distinct()

    val tokens = LinkedHashSet<String>()

    // 1. Thêm tất cả ký tự đơn (1 ký tự)
    allChars.forEach { tokens.add(it.toString()) }

    val TARGET_TOKENS = 50000

    // 2. Tổ hợp 2 ký tự (c1 + c2)
    val shuffledBase = allChars.shuffled()
    for (c1 in shuffledBase) {
        for (c2 in shuffledBase) {
            tokens.add("$c1$c2")
            if (tokens.size >= TARGET_TOKENS) break
        }
        if (tokens.size >= TARGET_TOKENS) break
    }

    // 3. Tổ hợp 3 ký tự (nếu cần đạt đủ 50.000 tokens)
    if (tokens.size < TARGET_TOKENS) {
        val samplePool = if (shuffledBase.size > 200) shuffledBase.take(200) else shuffledBase
        for (c1 in samplePool) {
            for (c2 in samplePool) {
                for (c3 in samplePool) {
                    tokens.add("$c1$c2$c3")
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

// Tự động sinh từ điển ngẫu nhiên khi cấu hình / build
generateRandomDictionary(listOf(file("dict_random.txt"), rootProject.file("dict_random.txt")))

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
