# Instagram Native Engine (447.0.0.55.81 DEX)

## Setup
```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
}
```

## Usage
```kotlin
val engine = InstagramEngine()

// 1. Login
val login = engine.login("username", "password")

// 2. Get Avatar Info
val avatar = engine.getAvatar(login.userId)
println("Avatar HD: ${avatar?.hdAvatarUrl}")

// 3. Upload Avatar
val imageBytes = java.io.File("avatar.jpg").readBytes()
val uploadResult = engine.uploadAvatar(imageBytes)

// 4. Remove Avatar
engine.removeAvatar()
```
