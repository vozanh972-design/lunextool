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
Debug APK ở Bước 2 dùng để cài thử, không nên phát hành. Để có APK **release**
đã ký:
1. Tạo keystore (nếu chưa có):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias cayxu \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Encode keystore sang base64:
   ```bash
   base64 -i release.keystore -o release.keystore.base64   # macOS
   base64 -w0 release.keystore > release.keystore.base64   # Linux
   ```
3. Vào repo GitHub → **Settings > Secrets and variables > Actions** → tạo 4 secret:
   - `KEYSTORE_BASE64` = nội dung file `release.keystore.base64`
   - `KEYSTORE_PASSWORD` = mật khẩu keystore
   - `KEY_ALIAS` = alias (ví dụ `cayxu`)
   - `KEY_PASSWORD` = mật khẩu key
4. Push lại code (hoặc **Run workflow** lần nữa) → Actions sẽ tự build thêm
   artifact `CayXu-release-apk` đã ký sẵn, sẵn sàng phát hành.

⚠️ Không commit file keystore hay mật khẩu vào Git — luôn dùng GitHub Secrets
như trên (`.gitignore` đã loại trừ sẵn `*.keystore`, `*.jks`).

## Một lưu ý quan trọng
Mô hình app (mua key kích hoạt → "cày" ra Xu quy đổi tiền → mời bạn bè nhận thưởng)
khá giống mô-tip của nhiều app "kiếm tiền online" từng bị phản ánh là lừa đảo tại
Việt Nam, đặc biệt khi phần thu nhập/nhiệm vụ hiện vẫn là dữ liệu demo chứ chưa có
cơ chế trả thưởng thật. Phần khung ứng dụng và luồng đăng nhập bằng key mình đã build
đầy đủ theo đúng yêu cầu kỹ thuật; nhưng nếu bạn định phát hành app "trả tiền/quy đổi
Xu ra tiền thật" cho người dùng, bạn nên đảm bảo cơ chế trả thưởng minh bạch và tuân
thủ quy định pháp luật hiện hành, để tránh rủi ro pháp lý cho chính bạn.
