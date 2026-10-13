# AUTOLUNEX – Jetpack Compose (Chọn ngôn ngữ + Nhập key)

## Cài đặt
1. Chép các file UI:
   - `AlxDesign.kt` → `.../ui/design/` (màu, font Manrope DÙNG CHUNG, animation helper, SvgIcon, PrimaryButton)
   - `LanguageSelectionScreen.kt` → `.../ui/screens/welcome/`
   - `LicenseScreen.kt` → `.../ui/license/`
   - `VerifyingScreen.kt` → `.../ui/verify/` (một màn kiểm tra key: ổ khóa → tích xanh hoặc X đỏ)
   - `AppFlow.kt` → `.../app/` (luồng điều hướng toàn app, xem mục "Luồng mở app")
   - `ActivationSuccessScreen.kt` → `.../ui/success/` (chào mừng + chuyển cảnh vào Trang chủ)
   Mọi màn đều lấy font từ `Manrope` trong `AlxDesign.kt`, màn mới chỉ cần dùng `type(...)`.
2. Tải font **Manrope** từ Google Fonts. Lấy file variable `Manrope[wght].ttf`, đổi tên thành `manrope.ttf` và đặt vào `app/src/main/res/font/`.
   Chỉ cần đúng 1 file này; code tự chọn độ đậm Medium/SemiBold/Bold/ExtraBold bằng trục wght. Đừng chép cùng một file variable thành 4 tên khác nhau, chữ sẽ bị mảnh.
3. Dependencies (BOM Compose 2024.06 trở lên):
   ```
   implementation(platform("androidx.compose:compose-bom:2024.09.00"))
   implementation("androidx.compose.ui:ui")
   implementation("androidx.compose.foundation:foundation")
   implementation("androidx.compose.animation:animation")
   implementation("androidx.compose.ui:ui-tooling-preview")
   debugImplementation("androidx.compose.ui:ui-tooling")
   ```
   Không cần Material. `minSdk 24` trở lên.
4. Màn luôn sáng: không đọc theme hệ thống, không có dark mode.

## Số đo lấy từ thiết kế (khung 390 × 844 dp)
Nền `#F3F5F9`, xanh chính `#1F4BD8`, chữ `#0F172A`. Padding ngang 20dp. Panel minh họa cao 204dp, bo 26dp.
Thẻ chọn bo 20dp, viền 1.5dp. Nút "Tiếp tục" cao 56dp, bo 18dp.

## Animation
Quả cầu quay 9s, chấm chạy quanh 10s, thẻ chào nổi 5s, ba chấm đang gõ + bong bóng trả lời lặp 7s,
đổi ngôn ngữ thì chữ trong thẻ chào mờ vào + trượt lên 6dp (350ms), thẻ bấm nhún 0.985 (120ms).
Người dùng tắt animation hệ thống (Animator duration scale = 0) thì mọi chuyển động dừng ở trạng thái tĩnh.

## Màn nhập key (LicenseScreen)
`LicenseRoute` có validate demo (≥ 8 ký tự); thay bằng gọi API thật.
Sai key: ô đỏ + rung + thông báo lỗi. Đúng key: gọi `onSuccess`.
Ô key cao 56dp bo 16dp, viền 1.5dp; focus có viền xanh 2dp + quầng. Điện thoại mini trượt lên, cuộn, thanh 95% chạy, chạy 1 lần 6s rồi dừng.
Khi bàn phím bật, nội dung cuộn được (`imePadding`).

## Màn kiểm tra key (VerifyingScreen.kt) – MỘT màn, 3 trạng thái
Chỉ có ổ khóa (144dp, căn giữa) và một dòng chữ. Không có mô tả, bước, key hay chú thích.
- `Checking`: ổ khóa trắng trong lõi xanh, cung xanh xoay; chữ "Đang kiểm tra…".
- `Success`: quai khóa mở, ổ khóa thu nhỏ rồi biến mất, dấu tích trắng vẽ ra; chữ xanh "Xác thực thành công". Sau ~1,9 s gọi `onSuccess` (sang `ActivationSuccessScreen` rồi Trang chủ).
- `Error`: ổ khóa rung, nền và lõi chuyển đỏ, dấu X vẽ ra; chữ đỏ "Key không hợp lệ". Sau ~1,7 s gọi `onFailure` (quay về màn nhập key để hiện lỗi).
Dùng `VerifyingRoute(key, validate, onSuccess, onFailure)`; `validate` là hàm suspend gọi server. Hiện tối thiểu ~1,2 s cho trạng thái kiểm tra.
Mỗi chuyển trạng thái chạy đúng một lần. Tắt animation hệ thống thì nhảy thẳng tới hình cuối.

## Màn chào mừng + vào Trang chủ (ActivationSuccessScreen.kt)
```kotlin
ActivationSuccessScreen(
    username = "username",
    daysLeft = 25,
    onFinished = { /* Trang chủ đã hiện đủ, có thể nhận chạm */ },
    home = { HomeScreen() },   // Trang chủ THẬT của app, màn này không vẽ lại bản sao
)
```
Chạy MỘT lần, không lặp (tổng 3,45 s):
- 0 → 3,0 s: lời chào. Avatar bật lên, tick vẽ dần, 5 phần tử trang trí to dần 0,2 → 1 rồi đứng yên (không lơ lửng).
  Bố cục bất đối xứng: trên 2 (TH, like), giữa 1 (+25 xu), dưới 2 (+ Follow, MK).
- 3,0 → 3,45 s: lời chào mờ đi, Trang chủ thật mờ vào và trồi lên 12dp, rồi gọi `onFinished`.
(Đã bỏ cú điện thoại mini phóng to thành Trang chủ vì thừa sau màn chào mừng và nặng máy.)
Trong lúc chạy, chạm bị chặn. Tắt animation hệ thống thì chỉ hiện lời chào ~0,5 s rồi vào thẳng Trang chủ.
`home()` được tải sẵn từ đầu (ẩn) để không giật khi hiện. Bố cục co theo bề rộng màn hình thật so với khung thiết kế 390dp.

## Luồng mở app (AppFlow.kt)
```
LẦN ĐẦU : Chọn ngôn ngữ → Nhập key → Kiểm tra → Chào mừng → Trang chủ
MỞ LẠI   : Kiểm tra → Trang chủ          (không ngôn ngữ, không nhập key, không chào mừng)
KEY SAI  : Kiểm tra → X đỏ → Nhập key (ô đỏ + rung + thông báo)
```
Cách hoạt động:
1. `AppFlow` đọc `SessionStore` ngay khi mở app: có `licenseKey` thì vào thẳng `VerifyingRoute` với `firstTime = false`;
   chưa có `language` thì vào Chọn ngôn ngữ; còn lại vào Nhập key.
2. Nhập key xong, `LicenseRoute` gọi `onActivated(key)` rồi sang `VerifyingRoute(firstTime = true)`. `LicenseRoute` KHÔNG tự gọi server nữa.
3. Kiểm tra đúng: lưu `licenseKey`. Nếu `firstTime` thì sang `ActivationSuccessScreen` (chào mừng + vào Trang chủ), nếu mở lại thì vào Trang chủ luôn.
4. Kiểm tra sai: nếu là key vừa nhập, giữ lại key trong ô; nếu là key cũ đã lưu (hết hạn/bị khóa), xóa key đã lưu rồi cho nhập lại.
   Cả hai trường hợp đều quay về `LicenseRoute(initialError = …)` để hiện lỗi.
5. Cài sẵn `SecurePrefsSessionStore` (dùng `EncryptedSharedPreferences` AES-256 mã hóa an toàn) và truyền `validate` gọi `AuthRepository.verifyKey()`.
6. Gọi trong `MainActivity`: `setContent { AppFlow(store, validate = { ... }, onBuy = { ... }, home = { CayXuNavGraph(startDestination = Routes.HOME) }) }`.
Lưu ý: nếu mở lại app mà mất mạng, `validate` sẽ nhận biết lỗi mạng và giữ lại key, cho người dùng vào Trang chủ (chống xóa nhầm key khi offline).

## Trang chủ (HomeScreen.kt)
```kotlin
HomeScreen(
    state = HomeUiState(
        username = "username", coins = "0", servicesActive = 5, servicesTotal = 5,
        daysLeft = 25, expiryText = "Hạn dùng 05/11/2026 · 11:51",
        hasUnread = true,
        today = DayEarnings(coins = 900, money = 140_000),       // kiếm được hôm nay
        recentDays = last7Days,                                  // List<DayEarnings> 7 ngày trước
        dailyGoal = null,                                        // hoặc DayEarnings(…) nếu người dùng đặt mục tiêu
        // tiles / wideTile có mặc định đúng như thiết kế (XSMM, GoLike, Tuongtaccheo, TikTok TTC, Nhiemvucheo)
    ),
    onTile = { tile -> /* tile.id: "xsmm", "golike", "ttc", "tiktok", "nvc" */ },
    onExplore = { }, onSeeAll = { }, onSearch = { }, onNotifications = { },
    selectedTab = HomeTab.Home,
    onTab = { tab -> /* chuyển Nhiệm vụ / Tiện ích / Hồ sơ */ },
)
```
Nối vào `AppFlow`: `home = { HomeScreen(state = vm.homeState, onTab = { … }) }`. Đây cũng là lambda `home` mà `ActivationSuccessScreen` dựng ngầm để mờ vào.
Dữ liệu thật (xu, số dịch vụ, ngày còn lại, hạn dùng, số tiền từng ô) lấy từ ViewModel và đưa vào `HomeUiState`; màn không tự gọi mạng.

Số đo từ thiết kế (khung 390 × 844 dp):
- Nội dung cuộn, padding ngang 20dp, trên 24dp, đáy chừa 104dp cho thanh điều hướng.
- Header: avatar tròn 44dp (2 chữ cái đầu của username), lời chào tự đổi sáng/trưa/chiều/tối theo giờ máy, chip PRO; 2 nút tròn 44dp (tìm kiếm, chuông có chấm đỏ 8dp khi `hasUnread`).
- Thẻ thống kê bo 22dp, 3 cột (Tổng xu / Dịch vụ / Còn lại) chia bằng vạch dọc, bên dưới là thanh "Hiệu suất hôm nay" cao 6dp và dòng hạn dùng.
- Hàng "Khám phá nhiệm vụ" bo 18dp, icon 40dp.
- "Truy cập nhanh": lưới 2 cột, mỗi ô cao 132dp bo 22dp, khoảng cách 12dp, vòng tròn mờ ở góc trên phải, 1 ô rộng ở cuối (Nhiemvucheo) có nhãn "Sẵn sàng".
- Thanh điều hướng: viên thuốc nổi cao 66dp, cách 2 bên 16dp và đáy 14dp, tab đang chọn nền #0F172A chiếm 2,5 phần có chữ, ba tab còn lại chỉ icon.
Không có animation riêng. Chạm vào ô, hàng, nút thì nhún 0.985 trong 120ms. Bóng của ô và thanh điều hướng chỉ gần đúng bản HTML (Compose không có bóng "spread" âm).
Màu riêng của từng dịch vụ (đỏ XSMM, hồng TTC, xám TikTok, xanh ngọc Nhiemvucheo) nằm trong `DefaultTiles`; thêm dịch vụ mới chỉ cần thêm một `HomeTile`.

### Hiệu suất hôm nay (tính thật, không hardcode)
`computePerformance(today, recent, goal)` trong `HomeScreen.kt`; `HomeUiState.performance` gọi nó sẵn.
- So sánh số xu và số tiền (đ) kiếm được HÔM NAY với mức một ngày bình thường của chính người dùng.
- Mốc so sánh: mục tiêu ngày người dùng tự đặt (`dailyGoal`) nếu có; không thì trung bình các ngày CÓ kiếm được trong 7 ngày gần nhất (ngày nghỉ không kéo mốc xuống).
- Mỗi loại: hôm nay / mốc, tối đa 100%. Hiệu suất = trung bình các tỉ lệ đó. Chỉ có xu hoặc chỉ có tiền thì tính theo loại có.
- Người dùng mới, chưa có lịch sử và chưa đặt mục tiêu: trả `null`, màn hiện "Chưa có dữ liệu" và thanh trống (không bịa số).
- Ví dụ: hôm nay 900 xu + 140.000đ; trung bình ngày có kiếm 1.100 xu và 175.000đ → (82% + 80%) / 2 = 81%.
Bạn cần ghi lại thu nhập theo ngày (xu + tiền) để truyền `today` và `recentDays`; màn không tự lưu.
Icon xu đổi thành đồng xu tròn xanh (vòng trắng mờ + hình thoi trắng), trùng với bản thiết kế trên Trang chủ, Nhiệm vụ và Chào mừng.

## Lưu ý quan trọng
Bóng đổ và độ cao dòng chữ có thể lệch nhẹ so với bản HTML.

## Build APK tự động bằng GitHub Actions (không cần cài Android Studio)

Repo đã có sẵn workflow `.github/workflows/build-apk.yml`. Chỉ cần đẩy code lên
GitHub là Actions tự build APK cho bạn tải về.

### Bước 1 – Đẩy code lên GitHub
```bash
git add .
git commit -m "Update AUTOLUNEX AppFlow & VerifyingScreen"
git push origin main
```

### Bước 2 – Xem kết quả build
1. Vào tab **Actions** trên repo GitHub → chọn workflow **Build APK** vừa chạy.
2. Đợi build xong → kéo xuống mục **Artifacts** → tải file `CayXu-debug-apk` về để cài thử.
