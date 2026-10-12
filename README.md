# AUTOLUNEX – Jetpack Compose (Chọn ngôn ngữ + Nhập key + Chào mừng)

## Cài đặt
1. Cấu trúc các file UI mới:
   - `AlxDesign.kt` → `com.cayxu.app.ui.design` (màu, font Manrope DÙNG CHUNG, animation helper, SvgIcon, PrimaryButton)
   - `LanguageSelectionScreen.kt` → `com.cayxu.app.ui.screens.welcome` (hoặc `ui/language`)
   - `LicenseScreen.kt` → `com.cayxu.app.ui.license`
   - `VerifyingScreen.kt` → `com.cayxu.app.ui.verify` (màn chờ kiểm tra key, ổ khóa + chìa)
   - `ActivationSuccessScreen.kt` → `com.cayxu.app.ui.success` (chào mừng + chuyển cảnh vào Trang chủ)
   Mọi màn đều lấy font từ `Manrope` trong `AlxDesign.kt`, màn mới chỉ cần dùng `type(...)`.
2. Tải font **Manrope** từ Google Fonts. Lấy file variable `Manrope[wght].ttf`, đổi tên thành `manrope.ttf` và đặt vào `app/src/main/res/font/`.
   Chỉ cần đúng 1 file này; code tự chọn độ đậm Medium/SemiBold/Bold/ExtraBold bằng trục wght. Đừng chép cùng một file variable thành 4 tên khác nhau, chữ sẽ bị mảnh.
3. Dependencies (BOM Compose 2024.06 trở lên):
   ```kotlin
   implementation(platform("androidx.compose:compose-bom:2024.09.00"))
   implementation("androidx.compose.ui:ui")
   implementation("androidx.compose.foundation:foundation")
   implementation("androidx.compose.animation:animation")
   implementation("androidx.compose.ui:ui-tooling-preview")
   debugImplementation("androidx.compose.ui:ui-tooling")
   ```
   Không cần Material. `minSdk 24` trở lên.
4. Trong `MainActivity`: gọi `enableEdgeToEdge()` trước `setContent { ... }`.
5. Màn luôn sáng: không đọc theme hệ thống, không có dark mode.

## Số đo lấy từ thiết kế (khung 390 × 844 dp)
Nền `#F3F5F9`, xanh chính `#1F4BD8`, chữ `#0F172A`. Padding ngang 20dp. Panel minh họa cao 204dp, bo 26dp.
Thẻ chọn bo 20dp, viền 1.5dp. Nút "Tiếp tục" cao 56dp, bo 18dp.

## Animation
Quả cầu quay 9s, chấm chạy quanh 10s, thẻ chào nổi 5s, ba chấm đang gõ + bong bóng trả lời lặp 7s,
đổi ngôn ngữ thì chữ trong thẻ chào mờ vào + trượt lên 6dp (350ms), thẻ bấm nhún 0.985 (120ms).
Người dùng tắt animation hệ thống (Animator duration scale = 0) thì mọi chuyển động dừng ở trạng thái tĩnh.

## Màn nhập key (LicenseScreen.kt)
`LicenseRoute` kết nối `LoginViewModel` xác thực key với server.
Sai key: ô đỏ + rung + thông báo lỗi. Đúng key: gọi `onSuccess`.
Ô key cao 56dp bo 16dp, viền 1.5dp; focus có viền xanh 2dp + quầng. Điện thoại mini trượt lên, cuộn, thanh 95% chạy.
Khung viền trên điện thoại đứng yên, hiển thị rõ ràng, chân máy mờ dần vào nền. Chạy 1 lần (6s) rồi dừng ở trạng thái cuối, tự chạy lại khi người dùng gõ key hoặc bấm xác thực.
Khi bàn phím bật, nội dung cuộn được (`imePadding`).

## Màn chờ kiểm tra key (VerifyingScreen.kt)
`VerifyingRoute(key, validate, onSuccess, onFailure)` chạy 3 bước (định dạng → máy chủ → kích hoạt PRO), mỗi bước hiện tối thiểu ~0,9 s.
Ổ khóa ở giữa: khi máy chủ xác nhận, chìa trượt vào ổ, xoay, quai khóa bật mở rồi gọi `onSuccess` (sau ~1,3 s).
`validate` là hàm suspend gọi server của bạn; `onFailure` đưa người dùng về màn nhập key để hiện lỗi.

## Màn chào mừng + chuyển vào Trang chủ (ActivationSuccessScreen.kt)
```kotlin
ActivationSuccessScreen(
    username = "username",
    daysLeft = 25,
    onFinished = { /* Trang chủ đã hiện đủ, có thể nhận chạm */ },
    home = { HomeScreen() },   // Trang chủ THẬT của app, màn này không vẽ lại bản sao
)
```
Chạy MỘT lần, không lặp (tổng 5,8 s):
- 0 → 3,8 s: lời chào. Avatar bật lên, tick vẽ dần, 5 phần tử trang trí to dần 0,2 → 1 rồi đứng yên (không lơ lửng).
  Bố cục bất đối xứng: trên 2 (TH, like), giữa 1 (+25 xu), dưới 2 (+ Follow, MK).
- 3,4 → 4,3 s: điện thoại mini (chính `home()` thu 61,54%, cắt đáy) trồi từ dưới lên.
- 4,5 → 5,8 s: phóng to đồng thời scale, dịch chuyển, bỏ cắt đáy và bỏ bo góc thành Trang chủ toàn màn hình, rồi gọi `onFinished`.
Trong lúc chạy, chạm bị chặn. Tắt animation hệ thống thì chỉ hiện lời chào ~0,5 s rồi vào thẳng Trang chủ.
`home()` được tải sẵn từ đầu (ẩn) để không giật khi trồi lên. Nếu Trang chủ nặng, thử bọc nó bằng
`CompositingStrategy.Offscreen` hoặc chụp thành ảnh tĩnh cho riêng đoạn chuyển cảnh.
Bố cục co theo bề rộng màn hình thật so với khung thiết kế 390dp.

## Build APK tự động bằng GitHub Actions (không cần cài Android Studio)

Repo đã có sẵn workflow `.github/workflows/build-apk.yml`. Chỉ cần đẩy code lên
GitHub là Actions tự build APK cho bạn tải về.

### Bước 1 – Đẩy code lên GitHub
```bash
git add .
git commit -m "Update AUTOLUNEX UI & ActivationSuccessScreen"
git push origin main
```

### Bước 2 – Xem kết quả build
1. Vào tab **Actions** trên repo GitHub → chọn workflow **Build APK** vừa chạy.
2. Đợi build xong → kéo xuống mục **Artifacts** → tải file `CayXu-debug-apk` về để cài thử.
