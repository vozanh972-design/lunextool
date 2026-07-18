# Cày Xu – Android App (Kotlin + Jetpack Compose)

## Mở project
1. Mở **Android Studio** (bản mới nhất, hỗ trợ AGP 8.5+ / Kotlin 1.9+).
2. `File > Open` → chọn thư mục `CayXu` (thư mục chứa `settings.gradle.kts`).
3. Android Studio sẽ tự tạo Gradle Wrapper khi Sync lần đầu (repo này không kèm sẵn
   `gradlew`/`gradle-wrapper.jar` để giữ dung lượng nhỏ). Nếu Studio không tự hỏi,
   vào `File > Sync Project with Gradle Files`.
4. Build APK Release: `Build > Generate Signed Bundle / APK... > APK`, tạo hoặc chọn
   keystore, chọn build type `release`.

## Cấu trúc chính
- `data/api/ApiService.kt` – định nghĩa **duy nhất 1 endpoint** `POST api/verify_key.php`.
- `data/api/RetrofitClient.kt` – base URL `https://lunex.io.vn/` (không hardcode nơi khác).
- `data/model/VerifyKeyResponse.kt` – map đầy đủ toàn bộ field JSON server trả về.
- `data/local/SecurePrefs.kt` – lưu key bằng `EncryptedSharedPreferences`.
- `util/DeviceUtils.kt` – lấy `device_id` bằng `Settings.Secure.ANDROID_ID`.
- `ui/screens/login` – màn hình Login (giống ảnh mẫu 1).
- `ui/screens/home` – màn hình Home (giống ảnh mẫu 2). Menu, "Nhiệm vụ nổi bật",
  biểu đồ thu nhập và "Lịch sử gần đây" đang dùng **dữ liệu demo tĩnh** vì đề bài
  không cung cấp endpoint tương ứng — chỉ phần đăng nhập/xác thực key mới gọi API thật.
- `ui/screens/tasks|wallet|friends|account` – các tab còn lại trong Bottom Navigation
  hiện là màn hình khung (placeholder), sẵn sàng để nối API thật khi bạn cung cấp.
- Status bar dùng thanh trạng thái thật của hệ điều hành, không có icon giờ/pin/wifi/sóng
  giả trong layout, đúng yêu cầu.

## Build APK tự động bằng GitHub Actions (không cần cài Android Studio)

Repo đã có sẵn workflow `.github/workflows/build-apk.yml`. Chỉ cần đẩy code lên
GitHub là Actions tự build APK cho bạn tải về.

### Bước 1 – Tạo repo và đẩy code lên GitHub
```bash
cd CayXu
git init
git add .
git commit -m "Init CayXu app"
git branch -M main
git remote add origin https://github.com/<username>/<ten-repo>.git
git push -u origin main
```
(Tạo repo trống trước trên github.com, rồi thay `<username>/<ten-repo>` cho đúng.)

### Bước 2 – Xem kết quả build
1. Vào tab **Actions** trên repo GitHub → chọn workflow **Build APK** vừa chạy
   (tự chạy mỗi khi push lên `main`, hoặc bấm **Run workflow** để chạy tay).
2. Đợi build xong (vài phút) → kéo xuống mục **Artifacts** → tải file
   `CayXu-debug-apk` về, giải nén ra sẽ có file `.apk` cài thử được ngay.

### Bước 3 (tuỳ chọn) – Build APK Release đã ký để phát hành thật
Debug APK ở Bước 2 dùng để cài thử, không nên phát hành. Toàn bộ bước này làm
**hoàn toàn trên GitHub**, không cần cài keytool/Android Studio ở máy bạn:

1. Vào tab **Actions** → chọn workflow **"1 - Generate Release Keystore (chỉ
   chạy 1 lần)"** → bấm **Run workflow** → chờ chạy xong (khoảng 30 giây).
   ⚠️ Chỉ chạy workflow này **đúng 1 lần**. Chạy lại sẽ tạo ra keystore khác,
   làm hỏng chữ ký của các bản release cũ.
2. Vào run vừa chạy → mục **Artifacts** → tải file
   `release-keystore-DELETE-AFTER-DOWNLOAD` về, giải nén ra sẽ có 2 file:
   `release.keystore` và `release.keystore.base64`.
3. Vào repo GitHub → **Settings > Secrets and variables > Actions** → **New
   repository secret**, tạo đủ 4 secret:
   - `KEYSTORE_BASE64` = mở file `release.keystore.base64` bằng Notepad, copy
     **toàn bộ nội dung** (1 dòng dài) dán vào.
   - `KEYSTORE_PASSWORD` = `CayXu@2026Secure!`
   - `KEY_ALIAS` = `cayxu`
   - `KEY_PASSWORD` = `CayXu@2026Secure!`
4. **Quan trọng:** quay lại Actions, mở run vừa tạo keystore, bấm nút **"..."**
   góc phải mục Artifacts → **Delete artifact** để xoá file
   `release-keystore-DELETE-AFTER-DOWNLOAD` khỏi GitHub (nó chứa keystore gốc,
   không nên để trên mạng lâu). Bạn tự giữ 1 bản `release.keystore` đã tải về
   ở nơi riêng, an toàn (không đăng lên đâu cả) — mất file này là mất luôn khả
   năng phát hành bản cập nhật hợp lệ sau này.
5. Push code (hoặc **Run workflow** lại trên workflow **Build APK**) → Actions
   sẽ tự build thêm artifact `CayXu-release-apk` đã ký sẵn.
6. Trong log của bước **"Print release APK signature SHA-256"** sẽ có dòng
   `Signer #1 certificate SHA-256 digest: ...` — copy đúng chuỗi hash đó (bỏ
   dấu `:`), dán vào biến `EXPECTED_SIGNATURE_SHA256` trong file
   `IntegrityGuard.kt`, rồi commit + push lại lần cuối để bật kiểm tra chữ ký.

⚠️ Không commit file keystore hay mật khẩu vào Git — luôn dùng GitHub Secrets
như trên (`.gitignore` đã loại trừ sẵn `*.keystore`, `*.jks`).

⚠️ Từ giờ về sau, **mọi bản cập nhật** phải build qua đúng workflow này với
đúng 4 secret trên (không đổi keystore) — nếu bạn generate keystore mới, chữ
ký APK sẽ đổi, và `IntegrityGuard` sẽ coi bản mới là "bị giả mạo", tự khoá app
với chính người dùng hợp lệ. Nâng cấp tính năng, sửa code bình thường (không
đổi keystore) thì hoàn toàn không ảnh hưởng gì đến cơ chế này.

## Một lưu ý quan trọng
Mô hình app (mua key kích hoạt → "cày" ra Xu quy đổi tiền → mời bạn bè nhận thưởng)
khá giống mô-tip của nhiều app "kiếm tiền online" từng bị phản ánh là lừa đảo tại
Việt Nam, đặc biệt khi phần thu nhập/nhiệm vụ hiện vẫn là dữ liệu demo chứ chưa có
cơ chế trả thưởng thật. Phần khung ứng dụng và luồng đăng nhập bằng key mình đã build
đầy đủ theo đúng yêu cầu kỹ thuật; nhưng nếu bạn định phát hành app "trả tiền/quy đổi
Xu ra tiền thật" cho người dùng, bạn nên đảm bảo cơ chế trả thưởng minh bạch và tuân
thủ quy định pháp luật hiện hành, để tránh rủi ro pháp lý cho chính bạn.
