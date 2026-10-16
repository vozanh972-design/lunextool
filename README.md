# AUTOLUNEX – Jetpack Compose (Chọn ngôn ngữ + Nhập key)

## Cài đặt
1. Chép các file UI:
   - `AlxDesign.kt` → `.../ui/design/` (màu, font Manrope DÙNG CHUNG, animation helper, SvgIcon, PrimaryButton)
   - `LanguageSelectionScreen.kt` → `.../ui/screens/welcome/`
   - `LicenseScreen.kt` → `.../ui/license/`
   - `VerifyingScreen.kt` → `.../ui/verify/` (một màn kiểm tra key: ổ khóa → tích xanh hoặc X đỏ)
   - `MainShell.kt` → `.../ui/main/` (khung 4 tab + MỘT thanh dock dùng chung)
   - `HomeScreen.kt` → `.../ui/home/` (Trang chủ)
   - `TasksScreen.kt` → `.../ui/tasks/` (Nhiệm vụ)
   - `UtilitiesScreen.kt` → `.../ui/utilities/` (Tiện ích)
   - `ProfileScreen.kt` → `.../ui/profile/` (Hồ sơ)
   - `HistoryScreen.kt` & `HistoryViewModel.kt` → `.../ui/history/` (Lịch sử xu)
   - `CoinLedger.kt` & `RoomCoinLedger.kt` → `.../data/` (Sổ xu Room & interface)
   - `AppFlow.kt` → `.../app/` (luồng điều hướng toàn app, xem mục "Luồng mở app")
   - `ActivationSuccessScreen.kt` → `.../ui/success/` (chào mừng + chuyển cảnh vào Trang chủ)
   - `XsmmLoginScreen.kt`, `XsmmAccountsScreen.kt`, `XsmmAddAccountSheets.kt`, `XsmmTiktokCheckScreen.kt`, `XsmmRunConfigTiktokScreen.kt`, `XsmmRunConfigFacebookScreen.kt` & `XsmmRunConfigInstagramScreen.kt` → `.../ui/xsmm/` (Màn đăng nhập, danh sách tài khoản 3 nền tảng XSMM, các tấm thêm/kiểm tra tài khoản và 3 màn cấu hình chạy riêng biệt)
   - `RunningOverlayWindow.kt` → `.../ui/overlay/` (Màn hình nổi khi đang chạy nhiệm vụ — bản đầy đủ + bản thu nhỏ kéo-thả được)
   Đổi `package` cho đúng app. Mọi màn đều lấy font từ `Manrope` trong `AlxDesign.kt`, màn mới chỉ cần dùng `type(...)`.
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
        today = DayEarnings(coins = 2_000_000, money = 30_000),  // xu + tiền (đ) kiếm được hôm nay
        // tiles / wideTile có mặc định đúng như thiết kế (XSMM, GoLike, Tuongtaccheo, TikTok TTC, Nhiemvucheo)
    ),
    onTile = { tile -> /* tile.id: "xsmm", "golike", "ttc", "tiktok", "nvc" */ },
    onExplore = { }, onSeeAll = { }, onSearch = { }, onNotifications = { },
)
```
Màn này KHÔNG có dock. Dock nằm ở `MainShell`, xem mục "Khung 4 tab và thanh dock dùng chung" bên dưới.
Dữ liệu thật (xu, số dịch vụ, ngày còn lại, hạn dùng, số tiền từng ô) lấy từ ViewModel và đưa vào `HomeUiState`; màn không tự gọi mạng.

Số đo từ thiết kế (khung 390 × 844 dp):
- Nội dung cuộn, padding ngang 20dp, trên 24dp, đáy chừa 104dp cho thanh điều hướng.
- Header: avatar tròn 44dp (2 chữ cái đầu của username), lời chào tự đổi sáng/trưa/chiều/tối theo giờ máy, chip PRO; 2 nút tròn 44dp (tìm kiếm, chuông có chấm đỏ 8dp khi `hasUnread`).
- Thẻ thống kê bo 22dp, 3 cột (Tổng xu / Dịch vụ / Còn lại) chia bằng vạch dọc, bên dưới là thanh "Hiệu suất hôm nay" cao 6dp và dòng hạn dùng.
- Hàng "Khám phá nhiệm vụ" bo 18dp, icon 40dp.
- "Truy cập nhanh": lưới 2 cột, mỗi ô cao 132dp bo 22dp, khoảng cách 12dp, vòng tròn mờ ở góc trên phải, 1 ô rộng ở cuối (Nhiemvucheo) có nhãn "Sẵn sàng".
Không có animation riêng. Chạm vào ô, hàng, nút thì nhún 0.985 trong 120ms. Bóng của ô chỉ gần đúng bản HTML (Compose không có bóng "spread" âm).
Màu riêng của từng dịch vụ (đỏ XSMM, hồng TTC, xám TikTok, xanh ngọc Nhiemvucheo) nằm trong `DefaultTiles`; thêm dịch vụ mới chỉ cần thêm một `HomeTile`.

### Hiệu suất hôm nay (tính thật, không hardcode)
`computePerformance(today)` trong `HomeScreen.kt`; `HomeUiState.performance` gọi nó sẵn. Tỉ giá và mốc nằm ở `PerformanceScale`:
- Quy đổi xu ra đồng: 1.000.000 xu = 10.000đ (100 xu = 1đ).
- Giá trị hôm nay (đ) = xu / 100 + tiền kiếm được (đ).
- Hiệu suất = giá trị / 100.000đ, tối đa 100%. Nghĩa là phải kiếm cỡ 10.000.000 xu (hoặc tương đương bằng tiền) trong ngày mới chạm 100%; kiếm bình thường chỉ ra vài %.
- Ví dụ: 2.000.000 xu (20.000đ) + 30.000đ = 50.000đ → 50%. Ví dụ 900 xu + 140.000đ → 140%, bị chặn ở 100%.
Muốn đổi mốc thì sửa `FULL_SCORE_DONG`; đổi tỉ giá thì sửa `COINS_PER_DONG`.
Bạn cần ghi lại thu nhập trong ngày (xu + tiền) để truyền `today`; màn không tự lưu.
Icon xu là đồng xu tròn xanh (vòng trắng mờ + hình thoi trắng), trùng với thiết kế.

## Khung 4 tab và thanh dock dùng chung (MainShell.kt)
Chỉ có MỘT thanh dock trong cả app, vẽ ở `MainShell`. Màn Trang chủ, Nhiệm vụ (và Tiện ích, Hồ sơ sau này) chỉ vẽ nội dung,
chừa `DockClearance` (104dp) ở đáy và KHÔNG tự vẽ dock. Nhờ vậy sửa dock một chỗ là cả app đổi theo, không bao giờ bị hai dock.
```kotlin
MainRoute(
    home      = { HomeScreen(homeState, onTile = { … }, onExplore = { /* có thể đổi sang tab Nhiệm vụ */ }) },
    tasks     = { TasksScreen(tasksState, onService = { svc -> /* mở màn của nền tảng */ }) },
    utilities = { UtilitiesScreen(utilitiesState, onConfigure = { … }, onTool = { … }) },
    profile   = { ProfileScreen(profileState, onAction = { /* … */ }) },
)
```
Truyền `home = { MainRoute(…) }` vào `AppFlow` để thay Trang chủ cũ: sau chào mừng, người dùng vào khung 4 tab.
Dock: viên thuốc nổi cao 66dp, bo 33dp, cách 2 bên 16dp và đáy 14dp (cộng thanh điều hướng hệ thống). Tab đang chọn nền #0F172A, icon + chữ trắng, chiếm 2,5 phần; ba tab còn lại chỉ icon xám, mỗi tab 1 phần.
Tab đang chọn lưu bằng `rememberSaveable`. Mỗi tab dựng lại khi chuyển (vị trí cuộn không giữ); cần giữ thì thay `when(tab)` bằng `NavHost` hoặc `rememberSaveable` cho `ScrollState` từng màn.

## Nhiệm vụ (TasksScreen.kt)
```kotlin
TasksScreen(
    state = TasksUiState(
        coins = "1.250",
        today = DayEarnings(coins = 2_000_000, money = 30_000),   // CÙNG số với Trang chủ, hiệu suất hai nơi luôn khớp
        // services mặc định đúng thiết kế
    ),
    onService = { svc -> /* svc.id: "xsmm", "golike", "ttc", "tiktok", "nvc" */ },
)
```
- Tiêu đề 30sp, dòng phụ "N nền tảng kiếm xu đang kết nối" (N = số dịch vụ).
- Thẻ tóm tắt bo 20dp, 2 cột: Tổng xu (có icon xu) và Hiệu suất hôm nay (cùng hàm `computePerformance` với Trang chủ).
- KHÔNG có chip lọc (Tất cả / Đang chạy / Sẵn sàng đã bỏ), vẫn hiện đủ mọi nền tảng.
- Mỗi hàng bo 20dp, chỉ có: ô logo 46dp (nền nhạt, chữ màu dịch vụ), tên nền tảng, và bên dưới tên là hàng icon các mạng xã hội nó hỗ trợ. Không có số tiền/xu, không có "Check ›", không có chữ "Sẵn sàng", không có mô tả chữ.
- Trạng thái chưa hỗ trợ (`isSupported = false`, ví dụ **Nhiemvucheo**): Hàng chuyển sang tông xám, logo và tên màu xám, icon mạng làm mờ, hiển thị nhãn chip "Chưa hỗ trợ" ở góc phải và khóa hoàn toàn thao tác bấm (không có hiệu ứng nhún, không gọi onService). Ở Trang chủ, ô Nhiemvucheo cũng hiển thị nhãn "Chưa hỗ trợ" và bị khóa tương tự.
- Icon mạng: LOGO CHÍNH THỨC bạn cung cấp, đã cắt tròn và thu nhỏ đều nhau 128×128 (hiện ở 22dp, giữ nguyên màu, không vẽ lại). 3 file này đã nằm sẵn trong `res/drawable/` của gói:
  `ic_platform_facebook.png`, `ic_platform_instagram.png`, `ic_platform_tiktok.png`. Mỗi nền tảng khai báo `platforms = listOf(Platform.TikTok, Platform.Facebook, Platform.Instagram)`, thứ tự khai báo là thứ tự hiện.
  Tuongtaccheo và Nhiemvucheo hiện chỉ có Facebook. Thêm mạng mới (YouTube…): đưa logo vào cùng quy cách (vuông, cắt tròn, 128×128) rồi thêm một giá trị vào enum `Platform`. Nhớ đổi `import com.autolunex.app.R` cho đúng app.
- Danh sách mạng trong `DefaultServices` khớp bản thiết kế hiện tại (XSMM: TikTok, Facebook, Instagram; GoLike: Facebook, Instagram, TikTok; Tuongtaccheo: Facebook; TikTok TTC: TikTok; Nhiemvucheo: Facebook - Chưa hỗ trợ). Sửa theo thực tế.
- Số "95%" trên bản thiết kế chỉ là số minh họa. Trong app, hiệu suất tính thật từ `today` (xem mục "Hiệu suất hôm nay").
- Không có animation riêng; chạm hàng nhún 0.985 trong 120ms (chỉ áp dụng cho nền tảng đã hỗ trợ).

## Tiện ích (UtilitiesScreen.kt)
```kotlin
MainRoute(
    home = { … }, tasks = { … },
    utilities = {
        UtilitiesScreen(
            state = UtilitiesUiState(),                    // mặc định đúng thiết kế
            onConfigure = { /* nút "Cấu hình ngay": mở màn cấu hình nuôi tài khoản */ },
            onTool = { tool -> /* tool.id: "page_reg", "fb_care", "scenario" */ },
        )
    },
    profile = { … },
)
```
- Tiêu đề "Tiện ích mở rộng" 30sp, dòng phụ 14sp. Padding ngang 20dp. KHÔNG vẽ dock, chỉ chừa `DockClearance` ở đáy.
- Thẻ nổi bật: bo 24dp, padding 18dp, đổ bóng nhẹ. Nhãn "NỔI BẬT" (11sp, nền xanh nhạt), dòng "Đa luồng", tiêu đề 21sp, mô tả 13,5sp. Sửa chữ qua `FeaturedTool(...)`.
- Dải quy trình 3 bước (Lướt feed, Cảm xúc, Xem story): nền #F5F8FF, bo 16dp, vòng tròn 36dp. Hai bước đầu vòng đặc xanh, đường nối liền; bước cuối vòng trắng viền #9DB2EE, đường nối trước nó nét đứt. Ba bước cố định theo thiết kế, không nằm trong state.
- Nút "Cấu hình ngay": cao 50dp, bo 16dp, xanh chính, gọi `onConfigure`.
- "Công cụ khác": một thẻ trắng bo 20dp, mỗi dòng gồm ô icon 44dp (bo 13dp), nhãn nhóm viết hoa 10,5sp, tên 15sp, mô tả 12sp, mũi tên. Dòng cách nhau bằng đường kẻ 1dp. Thêm công cụ: thêm một `UtilityTool(...)` vào `tools` (id, tag, title, description, màu chữ/icon, màu nền ô, danh sách path icon).
- Path icon viết trên lưới 24×24 và PHẢI để cờ cung cách nhau (ví dụ `a4 4 0 0 0-4-4`, không viết `0 00-4-4`), nếu không `PathParser` đọc sai. Hàm `circle(cx, cy, r)` có sẵn để tạo hình tròn.
- Không có animation riêng; chạm nhún 0.985 trong 120ms. Bóng thẻ là xấp xỉ, hơi khác CSS một chút.

## Hồ sơ (ProfileScreen.kt)
```kotlin
val recent by ledger.observeRecent(2).collectAsStateWithLifecycle(emptyList())   // dữ liệu THẬT từ sổ xu

MainRoute(
    home = { … }, tasks = { … }, utilities = { … },
    profile = {
        ProfileScreen(
            state = ProfileUiState(
                username = session.username, handle = "@" + session.username,
                planBadge = "PRO", planName = "Thành viên PRO",
                daysLeft = license.daysLeft, expiryDate = license.expiryText,
                membershipProgress = license.daysLeft / license.totalDays.toFloat(),
                language = LanguageState.language,
                recent = recent,
            ),
            onAction = { action ->
                when (action) {
                    ProfileAction.Settings -> { /* mở màn Cài đặt */ }
                    ProfileAction.Terms -> { /* mở màn Điều khoản & chính sách */ }
                    ProfileAction.Logout -> { /* xóa key (SecurePrefs.clearKey()) rồi về màn đăng nhập */ }
                }
            },
            onLanguageChange = { lang -> LanguageState.setLanguage(context, lang) },
            onSeeAllHistory = { navController.navigate(Routes.COIN_HISTORY) },
        )
    },
)
```
- KHÔNG vẽ dock, chỉ chừa `DockClearance` ở đáy. Padding ngang 20dp, đỉnh 28dp.
- `ProfileUiState` không có giá trị mặc định: tên, gói, số ngày, ngày hết hạn, ngôn ngữ đều lấy từ dữ liệu thật (`SecurePrefs`, `LanguageState`).
- Danh tính: avatar tròn 68dp nền xanh nhạt, chữ cái đầu của `username`; tên 22sp ExtraBold kèm nhãn gói; dưới là `handle`. `planBadge = ""` thì ẩn nhãn.
- Thẻ thành viên: icon khiên 40dp, tên gói, "Còn N ngày · Hết hạn …", thanh tiến độ cao 5dp (`membershipProgress` 0..1).
- Mục "Lịch sử xu": tiêu đề 17sp, bên phải "Xem tất cả ›" xanh (vùng chạm 44dp) gọi `onSeeAllHistory` (luôn hiển thị để người dùng có thể bấm xem bất cứ lúc nào). Dưới là 2 dòng mới nhất (`recent`), mỗi dòng dùng chung `CoinEntryRow` với màn Lịch sử. Khi `recent` rỗng thì hiện thẻ "Chưa có lịch sử nhận xu".
- "Tài khoản & hỗ trợ" gồm 5 dòng cao 54dp:
  1. **Cài đặt** → `onAction(ProfileAction.Settings)`
  2. **Ngôn ngữ** (bên phải ghi ngôn ngữ đang dùng) → mở popup chọn ngôn ngữ (Tiếng Việt / English), cập nhật tức thì qua `LanguageState`.
  3. **Liên hệ hỗ trợ** → mở popup kênh liên hệ: Telegram (`https://t.me/thanhgmn`) và Zalo (`https://zalo.me/0931006827`).
  4. **Điều khoản & chính sách** → `onAction(ProfileAction.Terms)`
  5. **Đăng xuất** (chữ + icon đỏ, không mũi tên) → `onAction(ProfileAction.Logout)`: xóa key (`SecurePrefs.clearKey()`) rồi đưa về màn Login.
- Popup là tấm trượt từ đáy (BottomSheet), vẽ trong `Dialog` toàn màn hình nên phủ cả thanh dock. Chạm nền mờ hoặc bấm Back để đóng.
- Chạm nhún 0.985 trong 120ms; không animation riêng.

## Lịch sử xu (HistoryScreen.kt) & Sổ xu (CoinLedger)
Nguồn dữ liệu là **sổ xu** (`CoinLedger`), đọc dữ liệu thật từ cơ sở dữ liệu Room cục bộ (`RoomCoinLedger.kt`).

### 1. Ghi nhận xu khi hoàn thành nhiệm vụ
Mỗi lần một nền tảng (XSMM, GoLike, Tuongtaccheo, TikTok TTC, Nhiemvucheo) trả kết quả hoàn thành hoặc người dùng đổi thưởng:
```kotlin
val ledger = CoinLedgerProvider.get(context)
ledger.record(CoinEntry(
    kind = CoinEntryKind.Follow,                 // Follow, Like, Comment, Share, Watch, Redeem, Other
    title = "Theo dõi @linh.daily",
    coins = +40,                                 // âm khi đổi thưởng / trừ xu: -1000
    atMillis = System.currentTimeMillis(),
    source = "golike",                           // id nền tảng
    externalId = task.id,                        // id nhiệm vụ để tránh ghi trùng lặp
))
```
- Có `externalId` thì ghi lại cùng nhiệm vụ sẽ tự động bỏ qua (chỉ mục duy nhất trên `source + externalId`).

### 2. Quan sát luồng dữ liệu (Flow)
- Hồ sơ: đọc `ledger.observeRecent(2)`
- Màn Lịch sử: đọc `ledger.observeDay(ngay)` theo ngày đã chọn.
- Cả hai đều là `Flow`: khi có bản ghi mới, màn hình tự động cập nhật ngay lập tức.

### 3. Điều hướng màn Lịch sử xu
Màn Lịch sử xu nằm ngoài `MainRoute` (không có dock), được đăng ký qua `Routes.COIN_HISTORY` trong `CayXuNavGraph.kt`. Có nút Back trên góc trái và hỗ trợ BackHandler của Android để quay lại Hồ sơ.

## Cài đặt (SettingsScreen.kt) — lưu và đọc thật
Màn con toàn màn hình mở từ dòng "Cài đặt" ở Hồ sơ, không có dock, hiển thị NGOÀI `MainRoute` (qua `Routes.SETTINGS`):
- Khung: padding ngang 20dp, đỉnh 24dp. Đầu màn: nút quay lại tròn 44dp (viền, nền trắng) bên trái, tiêu đề "Cài đặt" 20sp ExtraBold căn giữa.
- 3 nhóm thiết kế Alx bo 22dp:
  1. **THÔNG BÁO**: Thông báo đẩy (`push`, xin quyền `POST_NOTIFICATIONS` trên Android 13+), Chạy ngầm (`background`, điều khiển `AppBackgroundService`).
  2. **GIAO DIỆN**: Giữ màn hình sáng (`keepAwake`, gọi `KeepScreenOn` ở gốc `MainActivity`), Giao diện ("Sáng cao cấp").
  3. **HỖ TRỢ & HỆ THỐNG**: Trung tâm hỗ trợ (Telegram `t.me/thanhgmn`), Điều khoản sử dụng, Chính sách bảo mật, và Phiên bản ứng dụng (đọc `versionName` thật của app).
- Công tắc 52×32dp bo 16dp: bật nền xanh chính, tắt nền #CBD2DF, nút tròn trắng 26dp trượt 150ms.
- Dữ liệu lưu thật qua `AppSettingsStore` (SharedPreferences `autolunex_settings`) và đồng bộ trực tiếp với `AppBackgroundService` & `AppAlertNotifier`.

## Đăng nhập XSMM (XsmmLoginScreen.kt)
Màn hình đăng nhập tài khoản hệ thống XSMM để lấy token làm nhiệm vụ:
- Giao diện hiện đại đồng bộ Design System Alx, hỗ trợ mở webview trực tiếp đến xsmm.net để lấy token an toàn.
- Tự động lưu phiên đăng nhập và cập nhật số dư xu.

## XSMM: danh sách tài khoản (XsmmAccountsScreen.kt)
Gộp chung cả 3 tab nền tảng (TikTok, Facebook, Instagram) trong một giao diện duy nhất, chuyển tab mượt mà qua `state.selectedPlatform`:
- **TikTok**: Có các chip lọc phân loại tài khoản ("TikTok", "TikTok Lite", "TikTok Studio"), thẻ trống viền icon khi chưa có nick, thanh công cụ dưới gồm 2 nút: "Cấu hình" và "Chạy".
- **Facebook & Instagram**: Không hiển thị chip lọc loại, thẻ trạng thái rỗng có nền màu nhạt bấm vào để mở form thêm tài khoản nhanh. Thanh dưới đáy thay nút "Chạy" bằng checkbox chọn "Tất cả" và nút thêm tài khoản (+).
- **Màu sắc nhận diện**:
  - TikTok và Facebook: Sử dụng màu xanh dương chủ đạo (`Alx.Blue`).
  - Instagram: Sử dụng màu hồng-magenta (`#C2255C`) đặc trưng cho tab, nút (+) và nút "Cấu hình".
- Thẻ tài khoản gọn gàng hiển thị trạng thái hoạt động (Live/Die), avatar, username và số liệu liên kết hệ thống.

## XSMM: tấm "Đăng nhập Facebook" / "Thêm tài khoản Instagram" (XsmmAddAccountSheets.kt)
Bộ 2 tấm trượt (bottom sheet) toàn màn hình hiển thị dưới dạng Dialog mượt mà:
- **XsmmFbLoginSheet**: Đăng nhập tài khoản Facebook hỗ trợ 3 tùy chọn định dạng (`Token`, `UID|PASS|2FA`, `UID|PASS|COOKIE`) hoặc tự động nhận diện định dạng từ dữ liệu dán vào. Hỗ trợ dán hàng loạt nhiều nick (mỗi dòng 1 nick).
- **XsmmIgAddSheet**: Thêm tài khoản Instagram theo định dạng Cookie hoặc Cookie|Proxy, hỗ trợ dán hàng loạt tài khoản với placeholder mẫu trực quan.
- **Tích hợp**: Cả 2 tấm là lớp giao diện độc lập phát sinh sự kiện qua `onSubmit`, kết nối trực tiếp với logic kiểm tra tài khoản Live và lưu trữ trong `XsmmAccountScreen.kt`.

## XSMM: Kiểm tra tài khoản — nhánh TikTok (XsmmTiktokCheckScreen.kt) — CHỈ THAY GIAO DIỆN, không đụng logic
**Lưu ý quan trọng về nguồn gốc màn này:** đây là code cho màn **"Kiểm tra tài khoản" của XSMM, nhánh TikTok** — KHÔNG PHẢI cho Golike. Trong app hiện tại, màn "Golike: kiểm tra tài khoản" chỉ là bản copy gần như y nguyên bố cục từ chính màn XSMM TikTok này (cùng 2 dòng xin quyền, cùng lưới chọn app TikTok/TikTok Lite/TikTok Studio, cùng tấm bottom-sheet). Vì vậy file Kotlin này được đặt tên và viết tài liệu theo đúng màn gốc là XSMM TikTok; nếu sau này cần dùng lại cho Golike thì tự copy/nối logic tương ứng bên phía Golike, không sửa trực tiếp file này.

`XsmmTiktokCheckScreen(state: XsmmTiktokCheckUiState, onDismiss, onGrantOverlay, onGrantAccessibility, onSelectApp, onCheckAccount, modifier)` — tấm bottom-sheet đè lên nền mờ (scrim), gồm:
- 2 dòng xin quyền: "Hiển thị trên ứng dụng khác" (`state.overlayGranted`) và "Dịch vụ Trợ năng" (`state.accessibilityGranted`) — mỗi dòng tự đổi giữa nhãn "Cấp quyền" (nút xanh, gọi `onGrantOverlay`/`onGrantAccessibility`) và badge "Đã cấp" khi đã được cấp. File KHÔNG tự xin quyền hệ thống (không có `ActivityResultLauncher`, không mở `Settings`) — bạn tự nối hành vi xin quyền thật vào 2 callback này.
- Lưới 3 ô chọn app (`TiktokAppVariant`: TikTok / TikTok Lite / TikTok Studio) — ô nào có trong `state.installedApps` mới bấm chọn được (`onSelectApp`), ô chưa cài sẽ mờ đi và không bấm được; ô đang chọn tô nền đen theo đúng phong cách thiết kế gốc. Việc app nào đã cài trên máy do bạn tự truyền vào qua `installedApps`, file không tự kiểm tra `PackageManager`.
- Nút "Kiểm tra tài khoản" ở dưới: chỉ bật khi `state.accessibilityGranted == true` (khớp đúng luật gốc: phải có quyền Trợ năng mới cho chạy); khi tắt còn có thêm dòng chữ nhỏ "Cấp quyền Trợ năng để tiếp tục". Bấm vào gọi `onCheckAccount` — file không tự mở app, không tự đọc @username, không tự lưu tài khoản.
- `onDismiss` gọi khi chạm vào nền mờ phía sau tấm — dùng để đóng màn, quay lại màn trước đó.

## XSMM: Cấu hình chạy — 3 file riêng theo nền tảng (XsmmRunConfigTiktokScreen.kt / XsmmRunConfigFacebookScreen.kt / XsmmRunConfigInstagramScreen.kt) — CHỈ THAY GIAO DIỆN, không đụng logic
**Quan trọng:** cả 3 file CHỈ là lớp giao diện. Không có logic chạy nhiệm vụ, không gọi API, không tự lưu cấu hình, không tự validate số nhập — mọi trạng thái (các switch bật/tắt, các số, chế độ đã chọn) đều đi vào qua `UiState` và đi ra ngoài qua các callback (`onXChange`, `onSave`); bạn tự nối với logic thật, không có gì được "ngầm" xử lý bên trong file.

Theo đúng hướng kiến trúc hiện tại của app — **giao diện dùng chung (cùng token màu/chữ `Alx`, cùng kiểu thẻ/switch/ô số) nhưng logic tách riêng theo từng màn** — nên màn "Cấu hình chạy" của XSMM được tách thành 3 file/3 composable độc lập theo từng nền tảng, KHÔNG gộp chung 1 màn có dropdown chuyển nền tảng như bản thiết kế canvas ban đầu:
- `XsmmRunConfigTiktokScreen(state: XsmmRunConfigTiktokUiState, ...)` — loại nhiệm vụ: Theo dõi (Follow) / Thích (Like) / Bình luận (Comment), đều là switch bật/tắt độc lập.
- `XsmmRunConfigFacebookScreen(state: XsmmRunConfigFacebookUiState, ...)` — loại nhiệm vụ gồm 14 switch độc lập: Like, Love, Care, Haha, Wow, Sad, Angry, Theo dõi, Comment, Share, Like Page, Tham gia nhóm, Cảm xúc comment, Đánh giá.
- `XsmmRunConfigInstagramScreen(state: XsmmRunConfigInstagramUiState, ...)` — loại nhiệm vụ là **chọn 1 trong 4** (`XsmmIgTaskMode`: Ngẫu nhiên (Tym/Follow/Comment) / Chỉ Follow / Chỉ Tym / Chỉ Comment), vẽ dạng radio chứ không phải switch vì 4 lựa chọn này loại trừ lẫn nhau theo đúng ảnh gốc — nếu thực tế bên logic cho bật nhiều cùng lúc thì đổi `mode` thành tập hợp rồi đổi `ModeRow` sang switch như 2 file kia.

Cả 3 màn giống nhau ở phần còn lại (copy y hệt nhau theo đúng ảnh gốc, không có logic gì khác nhau ở đây — chỉ khác nội dung chữ/field nếu bạn đổi):
- Thẻ "Loại nhiệm vụ" luôn có dòng phụ đề "Bật nhiều loại → xoay vòng; hết NV đợi 10s rồi sang loại kế".
- 6 ô số: Thời gian lấy nhiệm vụ / Thời gian làm nhiệm vụ (đơn vị giây), Số nhiệm vụ muốn làm / Số lần hoàn thành NV sẽ dừng (để trống = không giới hạn, placeholder báo rõ), Số lần hết NV sẽ dừng, Số job lỗi / nhả sẽ đổi acc.
- 2 switch cuối: "Lướt trước khi làm", "Trở về Home rồi lướt".
- Nút "Lưu cấu hình" cố định ở đáy màn, gọi `onSave`.

Mỗi file có một ô nhận diện nền tảng (`PlatformBadge`) ngay dưới tiêu đề — dùng đúng 3 file logo tròn `R.drawable.ic_platform_{tiktok,facebook,instagram}` đã có sẵn trong `res/drawable` (cùng bộ ảnh với màn Nhiệm vụ), không vẽ icon riêng — chỉ để xác nhận đang ở cấu hình của nền tảng nào, không bấm chuyển được (vì mỗi file giờ là 1 màn cố định cho đúng 1 nền tảng).

Cách dùng — mỗi platform tự quản state/ViewModel riêng của nó, ví dụ:
```kotlin
var tiktokState by remember { mutableStateOf(XsmmRunConfigTiktokUiState()) }

XsmmRunConfigTiktokScreen(
    state = tiktokState,
    onBack = onBack,
    onFollowChange = { tiktokState = tiktokState.copy(follow = it) },
    onLikeChange = { tiktokState = tiktokState.copy(like = it) },
    onCommentChange = { tiktokState = tiktokState.copy(comment = it) },
    onGetTaskWaitChange = { tiktokState = tiktokState.copy(getTaskWaitSeconds = it) },
    onWorkSecondsChange = { tiktokState = tiktokState.copy(workSeconds = it) },
    onTaskCountChange = { tiktokState = tiktokState.copy(taskCount = it) },
    onStopAfterEmptyChange = { tiktokState = tiktokState.copy(stopAfterEmptyTimes = it) },
    onErrorSwapChange = { tiktokState = tiktokState.copy(errorSwapAccountCount = it) },
    onStopAfterDoneChange = { tiktokState = tiktokState.copy(stopAfterDoneTimes = it) },
    onBrowseBeforeTaskChange = { tiktokState = tiktokState.copy(browseBeforeTask = it) },
    onBackHomeThenBrowseChange = { tiktokState = tiktokState.copy(backHomeThenBrowse = it) },
    onSave = { vm.saveTiktokRunConfig(tiktokState) },  // hàm lưu thật của bạn
)
```
`XsmmRunConfigFacebookScreen` và `XsmmRunConfigInstagramScreen` dùng y hệt cách này, chỉ đổi tên state/callback theo đúng field của từng file. Mỗi file có `@Preview` riêng ở cuối để xem nhanh.
- Ô số dùng `BasicTextField` chỉ nhận chữ số (`filter { it.isDigit() }`), chưa có giới hạn min/max hay validate nghiệp vụ — bạn tự thêm nếu cần.
- 3 file này hoàn toàn độc lập với nhau (không `import` lẫn nhau, mỗi file tự có các composable phụ trợ riêng như `Switch`, `NumberField`...) nên xoá/sửa 1 file không ảnh hưởng 2 file kia.
- Chưa biên dịch, bạn chạy thử và báo lỗi nếu có.

## Màn hình nổi khi đang chạy (RunningOverlayWindow.kt) — CHỈ THAY GIAO DIỆN, không đụng logic
**Quan trọng:** file này CHỈ là lớp giao diện, khớp đúng bản thiết kế canvas đã chốt (`GolikeOverlay.dc.html`). KHÔNG có: logic chạy nhiệm vụ thật, logic tạm dừng/tiếp tục thật, logic xin quyền hiển thị-đè-lên-app-khác (`SYSTEM_ALERT_WINDOW` / `Settings.canDrawOverlays`), logic tạo/gỡ overlay bằng `WindowManager`, logic lưu lại vị trí đã kéo. Tất cả đi vào qua `RunningOverlayUiState`, đi ra qua callback — **bạn phải tự nối lại file logic thật (service đang chạy nhiệm vụ, nơi tạo/điều khiển overlay) vào màn nổi mới này**, file không tự biết phải làm gì khi người dùng bấm nút hay kéo thả.

Cách dùng dự kiến — Service nền tạo MỘT system overlay window kiểu `TYPE_APPLICATION_OVERLAY`, to bằng cả màn hình thiết bị (`MATCH_PARENT` × `MATCH_PARENT`, nền trong suốt, `FLAG_NOT_TOUCH_MODAL`), rồi đặt `RunningOverlayWindow` làm nội dung `ComposeView` của cửa sổ đó:
```kotlin
RunningOverlayWindow(
    state = RunningOverlayUiState(
        mode = "Golike",
        accountLabel = currentAccount?.displayName,   // null = overlay tự hiện "Chưa chọn tài khoản"/"No account selected"
        doneTasks = session.doneCount,
        earnedCoins = session.earnedCoins,
        statusText = session.statusText,               // đã dịch sẵn đúng ngôn ngữ hiện tại từ phía logic
        paused = session.isPaused,
        expanded = overlayPrefs.expanded,
        dockedSide = overlayPrefs.dockedSide,
        language = appSettings.language,                // dùng chung AppLanguage đã có ở màn Chọn ngôn ngữ
    ),
    onReposition = { /* bắt đầu cho người dùng kéo cả cửa sổ overlay bằng WindowManager — hành vi thật, không vẽ ở đây */ },
    onCollapse = { overlayPrefs.expanded = false },
    onExpand = { overlayPrefs.expanded = true },
    onClose = { service.stopOverlay() },
    onPauseResume = { session.togglePause() },
    onDockSideChange = { side -> overlayPrefs.dockedSide = side },  // tự lưu lại để lần sau mở ra đúng vị trí
)
```

**Vì sao khung bao bằng cả màn hình:** để việc kéo-thả bản thu nhỏ xử lý HOÀN TOÀN bằng Compose offset bên trong, không cần gọi `WindowManager.updateViewLayout` theo từng khung hình khi kéo — đây là cách làm phổ biến cho overlay dạng "bong bóng nổi" (chat-head). Nút "Đổi vị trí" ở bản đầy đủ là chuyện khác (di chuyển cả cửa sổ overlay bằng `WindowManager`, không phải kéo trong Compose) nên cố ý để `onReposition` trống — đó là logic thật.

**2 trạng thái, đúng theo thiết kế canvas:**
- **Bản đầy đủ**: thẻ trắng bo góc, hàng tiêu đề (AUTOLUNEX + version + 3 nút Đổi vị trí/Thu nhỏ/Đóng), 3 dòng thông tin (Chế độ / Tài khoản / Đã làm), dòng trạng thái nền xanh nhạt có chấm nháy (chỉ hiện khi `statusText` không rỗng), nút Tạm dừng/Tiếp tục dưới cùng.
- **Bản thu nhỏ**: logo vuông-bo-góc-nhẹ 56dp (chữ "A" + viền xanh lá nháy báo đang chạy + huy hiệu số NV đã làm) đậu sát mép màn hình — cố ý đặt tràn ra ngoài mép một đoạn (`BubbleOverhang = 28.dp`) để nửa logo bị chính mép màn hình thật "cắt" tự nhiên, không cần code clip riêng — kèm dòng trạng thái gắn liền bên cạnh (nền tối, chấm xanh lá nháy + `statusText`, tự cắt bớt kèm "…" nếu dài).

**Đậu phải vs đậu trái (`OverlaySide`):** đậu phải thì dòng trạng thái bên trái logo (đúng y hệt thiết kế gốc); đậu trái thì CHỈ đổi vị trí sang trái, nội dung/thông tin hiển thị giữ nguyên y hệt (vẫn logo + dòng trạng thái + số NV) — nhưng thứ tự trái-phải của logo và dòng trạng thái tự LẬT GƯƠNG (logo ló ra mép trái, dòng trạng thái nằm bên phải logo) để chữ trạng thái không bao giờ bị tràn ra ngoài màn hình — đúng như đã chốt khi duyệt thiết kế.

**Kéo-thả + tự hít mép (snap) — chỉ áp dụng cho bản thu nhỏ, bản đầy đủ không kéo được:** chạm giữ vào bản thu nhỏ và kéo ngang, thả tay ra thì tự `animateTo` trượt mượt (260ms) sang đúng mép gần hơn (so khoảng cách tâm logo với điểm giữa màn hình), rồi mới gọi `onDockSideChange(side)` — không bao giờ "nhảy cóc" sang mép kia trước khi animation xong. Toàn bộ xử lý bằng `Animatable` + `detectHorizontalDragGestures`, không đụng `WindowManager`.

**Animation — đúng yêu cầu "phải có animation khi đè vô hay bấm tương tác":**
- Mọi nút bấm (Đổi vị trí / Thu nhỏ / Đóng / Tạm dừng-Tiếp tục / chạm vào bản thu nhỏ) đều co nhẹ khi đè ngón tay (scale 0,88–0,96 tuỳ nút, 110ms) — cùng kiểu press-animation đã dùng ở các màn khác trong app.
- Chuyển cảnh đầy đủ ⇄ thu nhỏ: fade + scale (180ms mở ra, 140ms thu lại), không bật tắt đột ngột.
- Icon Tạm dừng ⇄ Tiếp tục đổi bằng `AnimatedContent` (fade 120ms), không giật.
- Chấm nháy báo "đang chạy" (`PulsingDot`) lặp lại 1,4s, cùng nhịp với bản thiết kế canvas.

**Ngôn ngữ:** `RunningOverlayUiState.language` dùng chung `AppLanguage` (`VI`/`EN`) đã có sẵn ở `LanguageScreen.kt` — đổi ngôn ngữ app thì overlay tự đổi theo. Chỉ các NHÃN CỐ ĐỊNH của overlay (Chế độ/Mode, Tài khoản/Account, Chưa chọn tài khoản/No account selected, Đã làm/Done, đơn vị NV/tasks, Tạm dừng/Pause, Tiếp tục/Resume) được dịch sẵn trong `OverlayStrings`. RIÊNG `statusText` (vd "Đang mở TikTok kiểm tra nick…") là nội dung ĐỘNG do logic thật tạo ra — overlay KHÔNG tự dịch, bạn tự đưa đúng chữ theo ngôn ngữ hiện tại từ nơi gọi trước khi gán vào state.
- Có 3 `@Preview`: bản đầy đủ, bản thu nhỏ đậu phải (tiếng Việt), bản thu nhỏ đậu trái (tiếng Anh) — xem nhanh cả 2 ngôn ngữ + 2 phía cùng lúc.
- Đã được nối vào `XsmmJobRunnerOverlayService` để chạy trực tiếp cho nhiệm vụ XSMM TikTok.

## Animation thanh dock (MainShell.kt)
Một viên thuốc nền mực #0F172A TRƯỢT giữa các tab như dock của Apple, không nhảy:
- Viên thuốc chạy bằng lò xo (dampingRatio 0,85, stiffness 300, khoảng 0,6 s, chậm dần ở cuối, hơi nảy nhẹ).
- Đang ở Hồ sơ mà bấm Trang chủ: viên thuốc trượt NGƯỢC về từ phải sang trái, lướt qua Tiện ích và Nhiệm vụ rồi dừng ở Trang chủ. Không có chuyện nhảy thẳng.
- Chỉ tab cũ (co lại 2,5 → 1 phần, chữ ngắn dần rồi mất) và tab mới (giãn ra 1 → 2,5 phần, chữ mở ra) đổi bề rộng; các tab ở giữa đứng yên, viên thuốc chỉ lướt qua.
- Icon đổi xám ↔ trắng theo khoảng cách tới viên thuốc.
- Chạm tab thì nhún 0.95 trong 120 ms. Tắt animation hệ thống (Animator duration scale = 0) thì nhảy thẳng tới trạng thái cuối.
Kích thước dock theo thiết kế: tổng cao 66dp (tab cao 52dp), bo 33dp, cách 2 bên 16dp và đáy 14dp. Viên thuốc rộng bằng tab đang chọn (2,5 phần).
Bản xem trước nằm ở bảng "Thanh dock" trên canvas: dock trên tự chạy một vòng (kể cả trượt ngược Hồ sơ về Trang chủ), dock dưới bấm thử được.
Các bảng Trang chủ / Nhiệm vụ / Tiện ích / Hồ sơ trên canvas là màn riêng nên dock ở đó chỉ chạy lúc màn vừa mở; hiệu ứng trượt đầy đủ xem ở bảng "Thanh dock" và trong app thật.

## Lưu ý quan trọng
Bóng đổ và độ cao dòng chữ có thể lệch nhẹ so với bản HTML.

## Build APK tự động bằng GitHub Actions (không cần cài Android Studio)

Repo đã có sẵn workflow `.github/workflows/build-apk.yml`. Chỉ cần đẩy code lên
GitHub là Actions tự build APK cho bạn tải về.

### Bước 1 – Đẩy code lên GitHub
```bash
git add .
git commit -m "Update AUTOLUNEX MainShell, TasksScreen & HomeScreen"
git push origin main
```

### Bước 2 – Xem kết quả build
1. Vào tab **Actions** trên repo GitHub → chọn workflow **Build APK** vừa chạy.
2. Đợi build xong → kéo xuống mục **Artifacts** → tải file `CayXu-debug-apk` về để cài thử.
