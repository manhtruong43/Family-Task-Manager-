# Lộ trình và Kế hoạch thực hiện dự án: Ứng dụng "Quản lý nhiệm vụ trong gia đình"

Dựa trên tài liệu **Hướng dẫn dự án nhóm Mobile App D22**, dưới đây là bản kế hoạch và lộ trình phát triển dự án di động đáp ứng đầy đủ các tiêu chí đầu ra của học phần (CLO1, CLO2, CLO3).

## 1. Tổng quan dự án
- **Tên dự án:** Ứng dụng Quản lý nhiệm vụ trong gia đình (Family Task Manager).
- **Mục tiêu:** Giúp phân chia, theo dõi và tạo động lực thực hiện việc nhà cho các thành viên trong gia đình.
- **Nền tảng:** Mobile App.
- **Quy mô nhóm:** 3 - 4 sinh viên.

## 2. Danh sách các chức năng (Yêu cầu bài toán)
1. **Phân chia công việc nhà:** Tạo, phân loại, đặt ưu tiên và giao việc.
2. **Theo dõi tiến độ:** Cập nhật trạng thái (chưa làm, đang làm, hoàn thành).
3. **Nhắc nhở:** Thông báo (Push notification) về hạn chót và công việc sắp tới.
4. **Thưởng cho thành viên:** Tích điểm, khen ngợi, phần thưởng khi hoàn thành nhiệm vụ.
5. **Danh sách cá nhân:** Theo dõi nhiệm vụ riêng biệt của từng người.
6. **Lập kế hoạch:** Tạo lịch trình hằng ngày/tuần cố định.
7. **Giao diện thân thiện:** Dễ sử dụng, tương tác tốt cho mọi lứa tuổi.
8. **Lịch sử công việc:** Lưu trữ và xem xét để đánh giá năng suất.
9. **Đa ngôn ngữ:** Hỗ trợ nhiều ngôn ngữ.

---

## 2.1. Kịch bản sử dụng thực tế (User Flow)

Để ứng dụng dễ hình dung, một luồng sử dụng cơ bản trong gia đình sẽ diễn ra như sau:

**Bước 1: Khởi tạo gia đình (Người quản lý / Admin)**
- **Đăng ký tài khoản:** Người đóng vai trò quản lý (ví dụ: Bố hoặc Mẹ) tải app và đăng ký tài khoản (qua Email/Google/SĐT).
- **Tạo nhóm gia đình:** Mẹ tạo một "Nhóm Gia đình" (Ví dụ: "Gia đình yêu thương"). Ứng dụng sinh ra một **Mã mời (Invite Code)** hoặc mã QR.
- **Thiết lập quy tắc:** Mẹ tạo sẵn một số phần thưởng hấp dẫn (Ví dụ: "Được đi xem phim cuối tuần", "Thêm 1 giờ chơi game") và gắn mức điểm cần để đổi thưởng.

**Bước 2: Các thành viên tham gia nhóm**
- Các thành viên khác (Bố, các Con) tải app về máy.
- Tại màn hình chính, họ chọn **"Tham gia gia đình"** và nhập Mã mời của Mẹ.
- Hệ thống tự động phân quyền: Mẹ là Admin (toàn quyền giao việc, duyệt việc), các con là Member (chỉ nhận việc, báo cáo).

**Bước 3: Vận hành và Quản lý công việc hàng ngày**
- **Giao việc:** Buổi sáng, Mẹ tạo công việc "Dọn phòng ngủ", gán trực tiếp cho "Bin" (con trai), độ ưu tiên "Cao", điểm thưởng "15 điểm", hạn chót "17:00".
- **Nhắc nhở & Nhận việc:** Hệ thống bắn **Push Notification** đến điện thoại Bin: *"Mẹ vừa giao cho bạn nhiệm vụ: Dọn phòng ngủ"*. Bin mở app, vào **Danh sách cá nhân** để xem các việc của mình trong ngày.
- **Cập nhật tiến độ:** Bắt đầu dọn, Bin kéo công việc sang trạng thái *"Đang làm"*. Khi xong, Bin đổi sang *"Đã hoàn thành"* (có thể bắt buộc tải lên một bức ảnh chụp phòng đã gọn gàng làm minh chứng).
- **Xác nhận (Approve) & Tích điểm:** Mẹ nhận được thông báo Bin đã xong việc. Mẹ xem ảnh, bấm "Xác nhận". Lập tức tài khoản của Bin được cộng 15 điểm.
- **Đổi thưởng:** Đến cuối tuần, Bin tích lũy được 50 điểm. Bin vào mục **Phần thưởng** trên app và bấm đổi lấy phần thưởng *"Thêm 1 giờ chơi game"*. Mẹ nhận được thông báo đổi thưởng và sẽ thực hiện trao thưởng ngoài đời thực.

**Bước 4: Theo dõi & Tổng kết**
- Cuối tháng, gia đình có thể vào mục **Thống kê** xem biểu đồ ai chăm chỉ nhất, kiếm được nhiều điểm nhất để vinh danh, tạo động lực tích cực.

---

## 3. Lộ trình thực hiện chi tiết (Dự kiến: 6 Tuần)

Lộ trình được thiết kế bám sát các tiêu chí đánh giá môn học:

### Tuần 1: Khởi tạo và Phân tích yêu cầu (Công việc chung)
- **Nhiệm vụ:**
  - Họp nhóm, thống nhất chủ đề, lựa chọn công nghệ (ví dụ: Flutter/React Native hoặc Native Android/Java/Kotlin) và cơ sở dữ liệu (Firebase hoặc SQLite/Room).
  - Phân tích yêu cầu chức năng và phi chức năng.
  - Phân công nhiệm vụ cụ thể cho từng thành viên.
- **Đầu ra:** Bảng phân công công việc, tài liệu đặc tả yêu cầu ban đầu.

### Tuần 2: Phân tích thiết kế kiến trúc và Giao diện (Mục tiêu đạt CLO1)
- **Nhiệm vụ chung:** Thiết kế kiến trúc tổng thể, sơ đồ cơ sở dữ liệu.
- **Nhiệm vụ cá nhân:** Mỗi thành viên sử dụng công cụ (Figma/Adobe XD) để thiết kế giao diện (UI) chi tiết cho chức năng mình đảm nhiệm.
- **Đầu ra:** Bản thiết kế giao diện (>75% giao diện được thiết kế đẹp, hợp lý), thiết kế kiến trúc và mô tả chức năng chi tiết.

### Tuần 3: Xây dựng Giao diện (UI) trên IDE (Mục tiêu đạt CLO2)
- **Nhiệm vụ chung:** Cài đặt phần khung ứng dụng, tạo các thành phần UI dùng chung, kết nối Github/Gitlab.
- **Nhiệm vụ cá nhân:** Viết mã giao diện cho các chức năng được phân công, áp dụng thành thạo nhiều kiểu Layout kết hợp. Đảm bảo giao diện xây dựng y hệt bản thiết kế.
- **Đầu ra:** Hoàn thiện > 75% màn hình UI trên ứng dụng.

### Tuần 4 - Tuần 5: Lập trình chức năng và Tích hợp hệ thống (Mục tiêu đạt CLO2)
- **Nhiệm vụ chung:** Cấu hình Database, thiết lập API (nếu có), đồng bộ mã nguồn giữa các thành viên.
- **Nhiệm vụ cá nhân:** 
  - Lập trình xử lý logic cho chức năng của mình.
  - Áp dụng các quy tắc bảo vệ dữ liệu cá nhân (phân quyền dữ liệu user).
  - Hoàn thiện > 75% chức năng đã mô tả, chạy thử nghiệm trên máy ảo và thiết bị thật.
- **Đầu ra:** Ứng dụng chạy hoàn thiện các luồng chức năng chính, tích hợp thành công các module của các thành viên.

### Tuần 6: Kiểm thử, Viết báo cáo và Bảo vệ dự án (Mục tiêu đạt CLO3)
- **Nhiệm vụ chung:** Test ứng dụng toàn diện (tích hợp), bắt lỗi (bugs). Tổng hợp thành báo cáo nhóm cuối kỳ.
- **Nhiệm vụ cá nhân:**
  - Viết tài liệu báo cáo cá nhân (đảm bảo >70% các đề mục chính, nội dung đầy đủ).
  - Chuẩn bị slide thuyết trình.
  - Chuẩn bị kịch bản bảo vệ (nắm rõ mã nguồn, cách triển khai chức năng để trả lời chất vấn của Giảng viên).
- **Đầu ra:** Bản cứng báo cáo, file cài đặt (.apk/.ipa), slide thuyết trình và sẵn sàng bảo vệ.

---

## 4. Bảng phân công nhiệm vụ chi tiết (Nhóm 3 thành viên)

**Quy trình chuẩn cho từng cá nhân (Chuẩn CDIO & PTIT):**  
Đặc tả chức năng ➔ Thiết kế UI (Figma) ➔ Xây dựng Code UI (Jetpack Compose) ➔ Lập trình Logic & Dữ liệu (MVVM + Room) ➔ Bảo vệ dữ liệu & Quyền riêng tư ➔ Viết báo cáo kỹ thuật ➔ Thuyết trình & Vấn đáp bảo vệ.

### 4.1. Bảng phân bổ 9 chức năng trọng tâm

| STT | Thành viên | Vai trò | Chức năng phụ trách | Khối lượng & Tỷ lệ đóng góp |
|:---:|:---|:---|:---|:---:|
| 1 | **Lỗ Mạnh Trường** | **Nhóm trưởng** (Architecture & Task Lead) | • Chức năng 1: Phân chia công việc nhà (Tạo, giao việc)<br>• Chức năng 2: Theo dõi tiến độ & Vòng đời nhiệm vụ<br>• Chức năng 5: Danh sách cá nhân & Xác nhận hoàn thành kèm ảnh minh chứng | **40%** |
| 2 | **Phạm Nguyễn Dũng** | **Thành viên** (Services & Gamification Lead) | • Chức năng 6: Lập kế hoạch theo tuần/ngày (Calendar view)<br>• Chức năng 3: Hệ thống nhắc nhở thông minh (Push Notification)<br>• Chức năng 4: Tích điểm & Đổi thưởng cho thành viên | **30%** |
| 3 | **Bùi Trung Kiên** | **Thành viên** (UI/UX Lead & Analytics) | • Chức năng thiết lập: Khởi tạo & Quản lý nhóm gia đình (Invite Code)<br>• Chức năng 8: Lịch sử công việc & Báo cáo thống kê năng suất (Biểu đồ Canvas)<br>• Chức năng 9 & 7: Đa ngôn ngữ (i18n) & Tinh chỉnh UI/UX Glassmorphism | **30%** |

---

### 4.2. Chi tiết phân công công việc từng thành viên

#### 👤 1. Lỗ Mạnh Trường — Nhóm trưởng (Team Leader)
* **Công việc chung nhóm:** 
  - Khởi tạo kiến trúc dự án tổng thể: Thiết lập Clean Architecture + MVVM + Hilt DI + Room SQLite Database.
  - Quản trị kho mã nguồn Git/GitHub, phân nhánh tính năng, điều phối tiến độ và ghép nối mã nguồn (merge code).
  - Quản lý phiên bản, kiểm tra mã nguồn (Code Review) và tổng hợp báo cáo kỹ thuật chung của nhóm.
* **Chức năng cá nhân phụ trách:**
  1. **Quản lý xác thực & Phân quyền:** Đăng ký, đăng nhập tài khoản, chọn vai trò (`ADMIN` - Bố/Mẹ / `MEMBER` - Con cái), chọn avatar emoji, mô phỏng OTP email (`LoginScreen.kt`, `LoginViewModel.kt`, `UserRepository.kt`, `UserDao.kt`).
  2. **Phân chia công việc nhà:** Tạo mới công việc, giao việc trực tiếp cho từng thành viên hoặc cả nhà, gán mức ưu tiên (Cao/Trung/Thấp), hạn chót và điểm thưởng (`CreateEditTaskScreen.kt`, `Task.kt`).
  3. **Vòng đời & Theo dõi tiến độ công việc:** Quản lý luồng 5 trạng thái (`TODO` ➔ `IN_PROGRESS` ➔ `PENDING_APPROVAL` ➔ `COMPLETED` / `REJECTED`), tải ảnh minh chứng thực tế khi hoàn thành, Admin duyệt/từ chối kèm lý do, tính năng thả biểu cảm emoji (`HomeScreen.kt`, `TaskDetailScreen.kt`, `TaskRepository.kt`, `TaskDao.kt`, `TaskReactionDao.kt`).
* **Bảo vệ dữ liệu & Quyền riêng tư:** Triển khai cơ chế phân quyền Role-based Access Control (RBAC): Chỉ Admin mới được tạo việc, giao việc và duyệt việc; Member chỉ được thao tác trên công việc được giao của mình.
* **Mục tiêu Chuẩn đầu ra:**
  - **CLO1:** Hoàn thành tài liệu đặc tả User Flow, thiết kế giao diện Figma luồng Auth và Task (>75% đạt chuẩn).
  - **CLO2:** Viết mã Jetpack Compose, xử lý StateFlow phản ứng, xây dựng Entity/DAO Room cho Users & Tasks, tích hợp Hilt.
  - **CLO3:** Báo cáo kỹ thuật chi tiết các module phụ trách, slide thuyết trình và bảo vệ xuất sắc trước hội đồng.

---

#### 👤 2. Phạm Nguyễn Dũng — Thành viên (Services & Gamification Lead)
* **Công việc chung nhóm:**
  - Nghiên cứu và cấu hình Android System Services, quản lý Notification Channel theo tiêu chuẩn Android 8.0+ (API 26+).
  - Thiết lập cơ chế lập trình bất đồng bộ Kotlin Coroutines & Flow, xử lý giao dịch dữ liệu an toàn (Room Transactions).
  - Phối hợp kiểm thử chức năng nền và tối ưu hiệu năng hệ thống.
* **Chức năng cá nhân phụ trách:**
  1. **Lập kế hoạch theo tuần/ngày (Planning & Calendar):** Xây dựng màn hình Calendar tuần tương tác, hiển thị trực quan các chấm chỉ báo (dots indicator) ngày có việc, bộ lọc danh sách công việc theo ngày hẹn (`CalendarScreen.kt`).
  2. **Hệ thống nhắc nhở thông minh (Push Notification):** Bắn thông báo đẩy local khi có việc mới được giao, cảnh báo hạn chót sắp đến, thông báo kết quả duyệt/từ chối công việc và thông báo kết quả đổi quà (`PushNotificationService.kt`, `NotificationDao.kt`, `NotificationItem.kt`).
  3. **Tích điểm & Hệ sinh thái phần thưởng (Gamification & Rewards):** Tự động cộng điểm vào tài khoản thành viên khi hoàn thành việc nhà, danh mục phần thưởng gia đình phong phú, quy trình gửi yêu cầu đổi thưởng và Admin phê duyệt (`RewardsScreen.kt`, `RewardsViewModel.kt`, `RewardRepository.kt`, `RewardDao.kt`, `Reward.kt`).
* **Bảo vệ dữ liệu & Quyền riêng tư:** Kiểm tra toàn vẹn số dư điểm (chặn việc trừ điểm âm, kiểm soát trạng thái giao dịch đổi thưởng), bảo vệ tính riêng tư của thông báo hệ thống.
* **Mục tiêu Chuẩn đầu ra:**
  - **CLO1:** Đặc tả luồng tương tác lịch và cơ chế đổi thưởng Gamification; hoàn thiện bản thiết kế UI Figma Calendar và Rewards.
  - **CLO2:** Viết mã Compose UI, tích hợp PushNotificationService, quản lý Room Transactions cho điểm thưởng.
  - **CLO3:** Soạn thảo báo cáo kỹ thuật cá nhân đầy đủ đề mục, slide thuyết trình và giải trình chi tiết về kiến trúc Service.

---

#### 👤 3. Bùi Trung Kiên — Thành viên (UI/UX Lead & Analytics)
* **Công việc chung nhóm:**
  - Thiết lập Design System toàn diện cho ứng dụng: Bảng màu chuẩn (Palette), kiểu chữ (Typography), hiệu ứng Glassmorphism (GlassCard, GlassBox, GlassGradientBackground).
  - Thiết kế Master Layout, khung điều hướng Navigation Compose và các thành phần UI dùng chung.
  - Cấu hình hạ tầng đa ngôn ngữ (Localization), đồng bộ chuỗi ký tự trên toàn hệ thống.
* **Chức năng cá nhân phụ trách:**
  1. **Khởi tạo & Quản lý nhóm gia đình:** Màn hình tạo gia đình mới với mã mời 6 chữ số ngẫu nhiên, cơ chế tham gia gia đình bằng mã mời, hiển thị danh sách thành viên trong nhóm kèm avatar và chức vụ (`FamilySetupScreen.kt`, `FamilyViewModel.kt`, `FamilyRepository.kt`, `FamilyDao.kt`, `FamilyGroup.kt`).
  2. **Lịch sử công việc & Báo cáo thống kê năng suất:** Lập trình vẽ biểu đồ nâng cao bằng Compose Canvas API (Donut Chart phân bổ % trạng thái công việc, Bar Chart so sánh điểm số tích lũy các thành viên), Bảng xếp hạng thi đua gia đình (`StatisticsScreen.kt`, `HomeViewModel.kt`).
  3. **Đa ngôn ngữ & Cài đặt hệ thống (Localization & Settings):** Hỗ trợ chuyển đổi song ngữ Tiếng Việt (mặc định) và Tiếng Anh tức thì, quản lý thông tin hồ sơ cá nhân, sao chép mã mời, tinh chỉnh giao diện người dùng thân thiện (`SettingsScreen.kt`, `strings.xml` vi/en, `Glassmorphism.kt`, `Color.kt`, `Theme.kt`).
* **Bảo vệ dữ liệu & Quyền riêng tư:** Cô lập dữ liệu giữa các nhóm gia đình bằng khóa ngoại `familyId`; mã mời gia đình được mã hóa và bảo mật; thông tin cá nhân và bảng thành tích chỉ hiển thị trong nội bộ gia đình.
* **Mục tiêu Chuẩn đầu ra:**
  - **CLO1:** Thiết kế hoàn chỉnh Design System và màn hình UI Family, Statistics, Settings trên Figma (>75% giao diện đẹp, tối ưu).
  - **CLO2:** Hiện thực hóa giao diện y hệt thiết kế bằng Compose, lập trình Custom Canvas vẽ biểu đồ mượt mà, cấu hình đa ngôn ngữ chuẩn XML.
  - **CLO3:** Viết báo cáo chuyên sâu về kỹ thuật vẽ Canvas và Design System, thuyết trình mạch lạc và bảo vệ sản phẩm trước hội đồng.

---

### 4.3. Ma trận trách nhiệm (RACI Matrix)

| Hạng mục / Module | Lỗ Mạnh Trường | Phạm Nguyễn Dũng | Bùi Trung Kiên |
|:---|:---:|:---:|:---:|
| **Kiến trúc hệ thống (MVVM + Hilt + Room)** | **R** / **A** | **C** | **C** |
| **Design System & Glassmorphism UI** | **C** | **C** | **R** / **A** |
| **Module 1: Đăng ký / Đăng nhập / Xác thực** | **R** / **A** | **I** | **C** |
| **Module 2: Phân chia & Giao nhiệm vụ** | **R** / **A** | **C** | **I** |
| **Module 3: Theo dõi tiến độ & Duyệt ảnh** | **R** / **A** | **C** | **I** |
| **Module 4: Lập kế hoạch theo lịch (Calendar)** | **I** | **R** / **A** | **C** |
| **Module 5: Dịch vụ Push Notification** | **C** | **R** / **A** | **I** |
| **Module 6: Tích điểm & Đổi phần thưởng** | **C** | **R** / **A** | **I** |
| **Module 7: Thiết lập nhóm gia đình (Invite Code)**| **C** | **I** | **R** / **A** |
| **Module 8: Thống kê & Vẽ biểu đồ Canvas** | **I** | **C** | **R** / **A** |
| **Module 9: Đa ngôn ngữ (i18n) & Cài đặt** | **I** | **I** | **R** / **A** |
| **Kiểm thử tích hợp & Báo cáo cuối kỳ** | **R** / **A** | **R** | **R** |

*(**R**: Responsible - Thực hiện chính; **A**: Accountable - Phê duyệt / Chịu trách nhiệm; **C**: Consulted - Tham vấn hỗ trợ; **I**: Informed - Nhận thông tin).*

---

## 5. Các tiêu chí trọng tâm cần lưu ý để đạt điểm cao (Exemplary 85% - 100%)
- **CLO1 (Phân tích & Thiết kế):** Thiết kế > 75% kiến trúc hệ thống rõ ràng; Giao diện UI thiết kế bằng công cụ (Figma) phải đẹp, logic, tối ưu trên nhiều kích thước màn hình.
- **CLO2 (Xây dựng & Triển khai):** Viết code UI giống hệt thiết kế. Áp dụng các layout phức tạp (thành thạo nhiều loại layout kết hợp). Tích hợp đầy đủ các dịch vụ (ví dụ Firebase), app chạy mượt, không crash.
- **CLO3 (Trình bày & Báo cáo):** Báo cáo đầy đủ đề mục (đặc tả, thiết kế, code, test). Khi bảo vệ, thuyết trình mạch lạc, trọng tâm, hiểu rõ từng dòng code chức năng mình làm để trả lời giảng viên.
