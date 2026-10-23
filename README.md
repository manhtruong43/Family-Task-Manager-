# 🏠 Family Task Manager

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2025.01.00-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-2.6.1-0288D1?style=for-the-badge&logo=databricks&logoColor=white)
![Hilt](https://img.shields.io/badge/Hilt-2.51.1-FF6F00?style=for-the-badge&logo=dagger&logoColor=white)

**Ứng dụng quản lý nhiệm vụ gia đình thông minh — phân công, theo dõi, thưởng điểm và tạo động lực cho mọi thành viên.**

_Dự án nhóm môn Phát triển Ứng dụng Di động — Học viện Công nghệ Bưu chính Viễn thông (PTIT)_

</div>

---

## 📋 Mục lục

- [Giới thiệu](#-giới-thiệu)
- [Tính năng chính](#-tính-năng-chính)
- [Luồng sử dụng](#-luồng-sử-dụng-user-flow)
- [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
- [Cơ sở dữ liệu](#-thiết-kế-cơ-sở-dữ-liệu)
- [Công nghệ sử dụng](#-công-nghệ-sử-dụng)
- [Cài đặt & Chạy thử](#-cài-đặt--chạy-thử)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Phân công thành viên](#-phân-công-thành-viên)
- [Ảnh chụp màn hình](#-ảnh-chụp-màn-hình)

---

## 🌟 Giới thiệu

**Family Task Manager** là ứng dụng Android giúp các gia đình:
- **Phân chia** công việc nhà một cách công bằng, minh bạch
- **Theo dõi** tiến độ thực hiện theo thời gian thực
- **Tạo động lực** thông qua hệ thống tích điểm & đổi thưởng
- **Gắn kết** các thành viên qua tính năng react, thống kê bảng xếp hạng

---

## ✨ Tính năng chính

| # | Tính năng | Mô tả |
|---|-----------|-------|
| 1 | 👥 **Nhóm gia đình** | Tạo nhóm với mã mời 6 chữ số; Admin/Member phân quyền tự động |
| 2 | 📋 **Phân chia công việc** | Tạo, giao việc, đặt độ ưu tiên (Cao/Trung/Thấp), điểm thưởng, hạn chót |
| 3 | 🔄 **Theo dõi tiến độ** | 5 trạng thái: Chưa làm → Đang làm → Chờ duyệt → Hoàn thành / Từ chối |
| 4 | 📸 **Xác nhận bằng ảnh** | Thành viên tải ảnh minh chứng; Admin xem và duyệt/từ chối |
| 5 | 🔔 **Thông báo đẩy** | Push Notification khi giao việc, duyệt, từ chối, trao thưởng |
| 6 | ⭐ **Hệ thống điểm thưởng** | Tích điểm tự động khi Admin duyệt; đổi phần thưởng cụ thể |
| 7 | 📅 **Lịch kế hoạch** | Calendar tuần tương tác; xem nhiệm vụ theo từng ngày |
| 8 | 📊 **Thống kê năng suất** | Donut Chart, Bar Chart, Bảng xếp hạng, % hoàn thành |
| 9 | 🌐 **Đa ngôn ngữ** | Tiếng Việt (mặc định) + Tiếng Anh |
| 10 | 😍 **Phản ứng cảm xúc** | React emoji (👍❤️🎉😄👏) trên mỗi nhiệm vụ |

---

## 🚶 Luồng sử dụng (User Flow)

```
┌─────────────────────────────────────────────────────────────┐
│  Bước 1: Đăng ký / Đăng nhập                                │
│  → Chọn vai trò (Admin - Bố/Mẹ | Member - Con)             │
│  → Chọn avatar emoji                                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
        ┌─────────────▼─────────────┐
        │    Đã có gia đình?        │
        └──────┬────────────┬───────┘
               │ Không      │ Có
               ▼            ▼
    ┌──────────────┐  ┌────────────────┐
    │ Tạo nhóm GĐ │  │ Nhập mã mời    │
    │ → Sinh mã    │  │ → Tham gia ngay│
    │   mời 6 số   │  └────────────────┘
    └──────┬───────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────┐
│  Màn hình chính (Home)                                       │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Tab: Tất cả | Của tôi | Cần duyệt | Đã xong           │ │
│  │ Filter theo thành viên                                  │ │
│  │ Hiển thị điểm & avatar các thành viên                  │ │
│  └─────────────────────────────────────────────────────────┘ │
│  Navigation: 🏠Home | 📅Calendar | 📊Statistics | ⚙Settings │
└──────────────────────────────────────────────────────────────┘
           │
    ┌──────▼──────────────────────────────────┐
    │           Vòng đời nhiệm vụ              │
    │  Tạo → Giao → 📋 Chưa làm              │
    │               → ⏳ Đang làm             │
    │               → 📸 Chờ duyệt (có ảnh)  │
    │               → ✅ Hoàn thành (+điểm)  │
    │               → ❌ Từ chối (làm lại)   │
    └─────────────────────────────────────────┘
```

---

## 🏗 Kiến trúc hệ thống

Ứng dụng áp dụng kiến trúc **MVVM (Model-View-ViewModel)** kết hợp **Repository Pattern**:

```
┌─────────────────────────────────────────────────────┐
│                      UI Layer                        │
│   Compose Screens → ViewModels (StateFlow/LiveData) │
└──────────────────────────┬──────────────────────────┘
                           │ observe / collect
┌──────────────────────────▼──────────────────────────┐
│                    Domain Layer                      │
│              Repository (Business Logic)             │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                    Data Layer                        │
│         DAO (Room)  +  Services (Notification)      │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                  Database Layer                      │
│              Room SQLite (AppDatabase)               │
└─────────────────────────────────────────────────────┘
```

### Sơ đồ phụ thuộc (Dependency Graph)

```
Hilt DI Container
│
├── AppDatabase ──────────────────────────────────────────┐
│   ├── UserDao                                           │
│   ├── TaskDao                                           │
│   ├── FamilyDao                                         │
│   ├── RewardDao                                         │
│   ├── NotificationDao                                   │
│   └── TaskReactionDao                                   │
│                                                         │
├── PushNotificationService                               │
│                                                         │
├── Repositories                                          │
│   ├── UserRepository(UserDao)                           │
│   ├── FamilyRepository(FamilyDao, UserDao,              │
│   │                    RewardRepository)                │
│   ├── TaskRepository(TaskDao, UserDao, NotifDao,        │
│   │                  TaskReactionDao, PushNotifService) │
│   └── RewardRepository(RewardDao, UserDao, NotifDao,   │
│                         PushNotifService)               │
│
└── ViewModels
    ├── LoginViewModel(UserRepository)
    ├── FamilyViewModel(FamilyRepository)
    ├── HomeViewModel(TaskRepo, UserRepo, FamilyRepo)
    └── RewardsViewModel(RewardRepository)
```

---

## 🗄 Thiết kế Cơ sở dữ liệu

```sql
users (id, name, email, avatarUrl, role, familyId, points, isEmailVerified)
  │
  ├──► family_groups (id, name, inviteCode, adminId, createdAt)
  │
  ├──► tasks (id, title, description, familyId, assignedToUserId,
  │           assignedToUserName, createdByUserId, priority, status,
  │           points, deadlineTimestamp, proofImageUrl, proofNote, createdAt)
  │
  ├──► rewards (id, title, description, familyId, pointsRequired, iconName)
  │
  ├──► reward_redemptions (id, rewardId, rewardTitle, userId, userName,
  │                        familyId, pointsUsed, status, timestamp)
  │
  ├──► notifications (id, userId, title, message, timestamp, isRead)
  │
  └──► task_reactions (id, taskId, userId, userName, emoji, timestamp)
```

#### Enums

| Enum | Giá trị |
|------|---------|
| `UserRole` | `ADMIN`, `MEMBER` |
| `TaskStatus` | `TODO`, `IN_PROGRESS`, `PENDING_APPROVAL`, `COMPLETED`, `REJECTED` |
| `TaskPriority` | `LOW`, `MEDIUM`, `HIGH` |
| `RedemptionStatus` | `PENDING`, `APPROVED`, `REJECTED` |

---

## 🛠 Công nghệ sử dụng

| Công nghệ | Phiên bản | Mục đích |
|-----------|-----------|----------|
| **Kotlin** | 2.0.0 | Ngôn ngữ lập trình chính |
| **Jetpack Compose** | BOM 2025.01.00 | Xây dựng giao diện khai báo (Declarative UI) |
| **Material Design 3** | BOM 2025 | Hệ thống thiết kế giao diện |
| **Room Database** | 2.6.1 | Cơ sở dữ liệu SQLite cục bộ |
| **Hilt (Dagger)** | 2.51.1 | Dependency Injection |
| **Navigation Compose** | 2.8.5 | Điều hướng màn hình |
| **KSP** | 2.0.0-1.0.21 | Annotation Processing (thay kapt) |
| **Coroutines + Flow** | 1.9.0 | Lập trình bất đồng bộ & reactive |
| **Coil** | 2.7.0 | Tải và hiển thị hình ảnh |
| **Canvas API** | Compose | Vẽ biểu đồ tùy chỉnh (Donut Chart, Bar Chart) |
| **NotificationManager** | Android | Push Notification local |
| **Glassmorphism UI** | Custom | Thiết kế giao diện Glassmorphism |

### Tại sao chọn những công nghệ này?

- **Room** thay vì Firebase: Đơn giản, không cần internet, phù hợp mạng LAN gia đình
- **Hilt** thay vì Koin: Tích hợp sâu với Android, compile-time safe
- **Compose** thay vì XML: Hiện đại, ít code hơn, UI reactive
- **KSP** thay vì KAPT: Nhanh hơn 2x khi build

---

## 🚀 Cài đặt & Chạy thử

### Yêu cầu

- Android Studio **Hedgehog** (2023.1.1) hoặc mới hơn
- JDK **17**
- Android SDK API Level **26+** (Android 8.0+)
- Gradle **8.8.0**

### Bước 1: Clone dự án

```bash
git clone https://github.com/<username>/family-task-manager.git
cd family-task-manager
```

### Bước 2: Mở bằng Android Studio

```
File → Open → Chọn thư mục "family management"
```

### Bước 3: Sync Gradle

```
Android Studio tự động sync. Nếu không: File → Sync Project with Gradle Files
```

### Bước 4: Chạy ứng dụng

```
Run → Run 'app'  (hoặc Shift+F10)
```

> **Lưu ý:** Ứng dụng yêu cầu Android API 26+ (Android 8.0). Hãy đảm bảo máy ảo hoặc thiết bị thật đáp ứng yêu cầu.

### Build APK

```bash
./gradlew assembleDebug
# APK được tạo tại: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Cấu trúc dự án

```
app/src/main/java/com/family/taskmanager/
│
├── FamilyTaskApplication.kt       # @HiltAndroidApp - khởi tạo Hilt
├── MainActivity.kt                # NavHost, điều hướng toàn cục
│
├── data/
│   ├── local/                     # DAO (Data Access Objects)
│   │   ├── AppDatabase.kt         # Room Database definition
│   │   ├── UserDao.kt
│   │   ├── TaskDao.kt
│   │   ├── FamilyDao.kt
│   │   ├── RewardDao.kt
│   │   ├── NotificationDao.kt
│   │   └── TaskReactionDao.kt
│   │
│   ├── model/                     # Entities & Enums
│   │   ├── User.kt                # UserRole enum
│   │   ├── Task.kt                # TaskStatus, TaskPriority enums
│   │   ├── FamilyGroup.kt
│   │   ├── Reward.kt              # Reward + RewardRedemption
│   │   ├── NotificationItem.kt
│   │   └── TaskReaction.kt
│   │
│   ├── repository/                # Business Logic Layer
│   │   ├── UserRepository.kt
│   │   ├── FamilyRepository.kt    # Tạo/Tham gia gia đình, init rewards
│   │   ├── TaskRepository.kt      # CRUD task, duyệt, từ chối, push notif
│   │   └── RewardRepository.kt    # Đổi thưởng, duyệt thưởng
│   │
│   └── service/
│       ├── PushNotificationService.kt   # Push notification local
│       └── EmailVerificationService.kt  # OTP simulation
│
├── di/
│   └── DatabaseModule.kt          # Hilt Module: cung cấp DB, DAO, Services
│
└── ui/
    ├── auth/
    │   ├── LoginScreen.kt         # Đăng ký/Đăng nhập + chọn avatar + role
    │   └── LoginViewModel.kt
    │
    ├── family/
    │   ├── FamilySetupScreen.kt   # Tạo nhóm hoặc tham gia bằng mã mời
    │   └── FamilyViewModel.kt
    │
    ├── home/
    │   ├── HomeScreen.kt          # Màn hình chính: tab, filter, task list
    │   └── HomeViewModel.kt       # Xử lý CRUD task, approve/reject
    │
    ├── task/
    │   ├── CreateEditTaskScreen.kt # Tạo nhiệm vụ: giao cho ai, ưu tiên, điểm
    │   └── TaskDetailScreen.kt    # Chi tiết: cập nhật trạng thái, ảnh minh chứng
    │
    ├── rewards/
    │   ├── RewardsScreen.kt       # Danh sách phần thưởng, đổi thưởng, duyệt
    │   └── RewardsViewModel.kt
    │
    ├── calendar/
    │   └── CalendarScreen.kt      # Lịch tuần tương tác, filter task theo ngày
    │
    ├── statistics/
    │   └── StatisticsScreen.kt    # Donut Chart, Bar Chart, Bảng xếp hạng
    │
    ├── settings/
    │   └── SettingsScreen.kt      # Hồ sơ, đổi avatar, mã mời, ngôn ngữ, logout
    │
    ├── navigation/
    │   └── Screen.kt              # Định nghĩa routes điều hướng
    │
    └── theme/
        ├── Color.kt               # Palette màu
        ├── Glassmorphism.kt       # GlassCard, GlassBox, GlassGradientBackground
        ├── Theme.kt               # MaterialTheme (dark/light/dynamic)
        └── Type.kt                # Typography

app/src/main/res/
├── values/strings.xml             # Chuỗi tiếng Việt
└── values-en/strings.xml          # Chuỗi tiếng Anh
```

---

## 👥 Phân công thành viên

> **Chu trình kỹ thuật cá nhân (Chuẩn CDIO & PTIT):**  
> `Khảo sát & Đặc tả chức năng` ➔ `Thiết kế UI/UX (Figma)` ➔ `Hiện thực hóa UI (Jetpack Compose)` ➔ `Lập trình Logic & Dữ liệu (MVVM + Room)` ➔ `Bảo vệ dữ liệu & Quyền riêng tư` ➔ `Kiểm thử & Báo cáo kỹ thuật` ➔ `Thuyết trình & Vấn đáp bảo vệ`

### 📊 Bảng tổng quan phân bổ nhiệm vụ & Đối chiếu Chuẩn đầu ra (CLO)

| STT | Thành viên | Vai trò chính | Chức năng phụ trách (Mỗi người 3 CN) | Công việc chung nhóm | Tỷ lệ đóng góp |
|:---:|:---|:---|:---|:---|:---:|
| 1 | **Lỗ Mạnh Trường** | **Nhóm trưởng** (Architecture & Task Lead) | 1. Đăng ký / Đăng nhập & Xác thực<br>2. Phân chia & Giao nhiệm vụ<br>3. Theo dõi tiến độ & Duyệt việc kèm ảnh | Khởi tạo khung MVVM, cấu hình Hilt & Room, quản lý Git/GitHub, ghép mã nguồn | **33.3%** |
| 2 | **Phạm Nguyễn Dũng** | **Thành viên** (Services & Gamification Lead) | 4. Lập kế hoạch theo tuần/ngày<br>5. Hệ thống nhắc nhở (Push Notification)<br>6. Tích điểm & Phần thưởng gia đình | Tích hợp Android System Services, quản lý thông báo, xử lý bất đồng bộ Coroutines/Flow | **33.3%** |
| 3 | **Bùi Trung Kiên** | **Thành viên** (UI/UX Lead & Analytics) | 7. Thiết lập nhóm & Quản lý gia đình<br>8. Thống kê năng suất & Vẽ biểu đồ Canvas<br>9. Đa ngôn ngữ (i18n) & Hệ thống UI Glassmorphism | Thiết kế Design System, Master Layout, Glassmorphism tokens, điều hướng Navigation Compose | **33.3%** |

---

### 1. 👨‍💻 Lỗ Mạnh Trường — Nhóm trưởng (Team Leader)

- **Trách nhiệm chung:** Khởi tạo kiến trúc dự án tổng thể (Clean MVVM + Repository Pattern + Hilt DI + Room SQLite), thiết lập và quản lý luồng nhánh Git/GitHub, tổ chức họp nhóm, điều phối tiến độ, rà soát mã nguồn (Code Review) và tổng hợp báo cáo chung.
- **Tuân thủ Chuẩn đầu ra:**
  - **CLO1 (Phân tích & Thiết kế):** Phân tích đặc tả yêu cầu, thiết kế kiến trúc phân tầng (UI - Domain - Data Layer), thiết kế wireframe & Figma cho luồng Authentication và Task Management (>75% đạt chuẩn tối ưu).
  - **CLO2 (Xây dựng & Triển khai):** Hiện thực hóa giao diện bằng Jetpack Compose, lập trình ViewModel xử lý StateFlow phản ứng, xây dựng Entity/DAO cho Users & Tasks, tích hợp Hilt Injection.
  - **CLO3 (Báo cáo & Bảo vệ):** Viết báo cáo chuyên sâu các module phụ trách (>70% đề mục theo chuẩn), chuẩn bị slide tổng quan kiến trúc và trả lời vấn đáp mã nguồn trước hội đồng.

#### Danh mục chức năng & Tệp mã nguồn phụ trách:

| # | Chức năng chi tiết | Mô tả nghiệp vụ | Màn hình (UI) | File mã nguồn chính |
|---|-------------------|-----------------|---------------|---------------------|
| **1** | **Xác thực & Phân quyền tài khoản** | Đăng ký, đăng nhập tài khoản, chọn vai trò (`ADMIN` - Quản lý / `MEMBER` - Thành viên), chọn Avatar emoji nhận diện, xác thực OTP email mô phỏng. | `LoginScreen` | `LoginViewModel.kt`<br>`UserRepository.kt`<br>`UserDao.kt`<br>`User.kt` |
| **2** | **Phân chia & Giao việc nhà** | Tạo mới công việc, phân công trực tiếp cho thành viên trong nhà hoặc gán chung, thiết lập mức độ ưu tiên (Cao/Trung/Thấp), hạn chót và điểm thưởng. | `CreateEditTaskScreen` | `HomeViewModel.kt`<br>`Task.kt`<br>`TaskPriority.kt` |
| **3** | **Vòng đời & Theo dõi tiến độ** | Quản lý 5 trạng thái công việc (`TODO` ➔ `IN_PROGRESS` ➔ `PENDING_APPROVAL` ➔ `COMPLETED` / `REJECTED`), tải ảnh minh chứng thực tế, Admin phê duyệt/từ chối kèm lý do, thả biểu cảm emoji. | `HomeScreen`<br>`TaskDetailScreen` | `TaskRepository.kt`<br>`TaskDao.kt`<br>`TaskReactionDao.kt`<br>`TaskReaction.kt` |

* **Bảo vệ dữ liệu cá nhân & Quyền riêng tư:** Áp dụng Role-based Access Control (RBAC) nghiêm ngặt; chỉ tài khoản `ADMIN` mới có quyền giao việc và duyệt/từ chối công việc; `MEMBER` chỉ có quyền cập nhật trạng thái việc của chính mình và tải ảnh minh chứng.

---

### 2. 👨‍💻 Phạm Nguyễn Dũng — Thành viên (Services & Gamification Lead)

- **Trách nhiệm chung:** Nghiên cứu và tích hợp các dịch vụ nền tảng hệ điều hành Android (Android System Services, NotificationManager), cấu hình Notification Channel theo chuẩn Android 8.0+ (API 26+), lập lịch kiểm tra hạn chót, đồng bộ giao dịch điểm số giữa Task và Reward.
- **Tuân thủ Chuẩn đầu ra:**
  - **CLO1 (Phân tích & Thiết kế):** Phân tích mô hình Gamification khuyến khích thành viên làm việc nhà, thiết kế luồng chuyển trạng thái đổi thưởng và sơ đồ tương tác Lịch kế hoạch tuần/ngày trên Figma.
  - **CLO2 (Xây dựng & Triển khai):** Viết mã UI màn hình Calendar và Rewards bằng Jetpack Compose, lập trình Room Transaction trừ điểm an toàn khi đổi thưởng, tích hợp PushNotificationService phát thông báo hệ thống local mượt mà.
  - **CLO3 (Báo cáo & Bảo vệ):** Hoàn thiện báo cáo kỹ thuật cá nhân về cơ chế Notification và Transaction Data, slide thuyết trình luồng đổi thưởng và giải trình mã nguồn bất đồng bộ Coroutines.

#### Danh mục chức năng & Tệp mã nguồn phụ trách:

| # | Chức năng chi tiết | Mô tả nghiệp vụ | Màn hình (UI) | File mã nguồn chính |
|---|-------------------|-----------------|---------------|---------------------|
| **4** | **Lập kế hoạch theo tuần/ngày** | Màn hình tương tác Calendar tuần linh hoạt, đánh dấu chấm (dots indicator) trực quan ngày có công việc, lọc và hiển thị danh sách nhiệm vụ cần làm theo từng ngày được chọn. | `CalendarScreen` | `CalendarScreen.kt`<br>`TaskRepository.kt` |
| **5** | **Hệ thống nhắc nhở thông minh** | Tự động bắn Push Notification khi có việc mới được giao, cảnh báo hạn chót sắp đến, thông báo kết quả duyệt/từ chối công việc và thông báo kết quả đổi quà. | Hệ thống Notification Bar | `PushNotificationService.kt`<br>`NotificationDao.kt`<br>`NotificationItem.kt` |
| **6** | **Tích điểm & Hệ sinh thái phần thưởng** | Cộng điểm tự động khi công việc được duyệt, khởi tạo kho phần thưởng gia đình mẫu, thành viên gửi yêu cầu đổi quà bằng điểm tích lũy, Admin xem xét và phê duyệt đổi quà. | `RewardsScreen` | `RewardsViewModel.kt`<br>`RewardRepository.kt`<br>`RewardDao.kt`<br>`Reward.kt` |

* **Bảo vệ dữ liệu cá nhân & Quyền riêng tư:** Kiểm soát ràng buộc toàn vẹn dữ liệu điểm số (Validation không để điểm âm); bảo vệ quyền riêng tư nội dung thông báo; chỉ thành viên sở hữu mới thấy tiến trình đổi quà cá nhân.

---

### 3. 👨‍💻 Bùi Trung Kiên — Thành viên (UI/UX Lead & Analytics)

- **Trách nhiệm chung:** Phụ trách kiến trúc giao diện (Master Layout, TopAppBar, BottomNavigationBar, Glassmorphism Design System), chuẩn hóa bảng màu hiện đại, kiểu chữ (Typography) và các component tái sử dụng; thiết lập cơ chế đa ngôn ngữ (Localization) cho toàn bộ ứng dụng.
- **Tuân thủ Chuẩn đầu ra:**
  - **CLO1 (Phân tích & Thiết kế):** Xây dựng bộ Design System đồng nhất trên Figma (Màu sắc, Iconography, Glass Card, Layout mẫu), thiết kế wireframe cho màn hình Thống kê biểu đồ, Cài đặt và Nhóm gia đình.
  - **CLO2 (Xây dựng & Triển khai):** Lập trình vẽ biểu đồ nâng cao tùy chỉnh bằng Compose Canvas API (Donut Chart phân bổ % trạng thái, Bar Chart so sánh điểm), phát triển bộ theme Glassmorphism cao cấp, cấu hình hoàn chỉnh tài nguyên đa ngôn ngữ XML.
  - **CLO3 (Báo cáo & Bảo vệ):** Soạn thảo báo cáo kỹ thuật chi tiết phần UI/UX, thuật toán biểu đồ Canvas và quản lý nhóm gia đình; thuyết trình và trình chiếu trực quan sản phẩm khi bảo vệ.

#### Danh mục chức năng & Tệp mã nguồn phụ trách:

| # | Chức năng chi tiết | Mô tả nghiệp vụ | Màn hình (UI) | File mã nguồn chính |
|---|-------------------|-----------------|---------------|---------------------|
| **7** | **Thiết lập & Quản lý nhóm gia đình** | Tạo nhóm gia đình mới sinh mã mời 6 chữ số ngẫu nhiên, cơ chế tham gia gia đình qua mã mời, hiển thị danh sách thành viên kèm avatar và vai trò. | `FamilySetupScreen` | `FamilyViewModel.kt`<br>`FamilyRepository.kt`<br>`FamilyDao.kt`<br>`FamilyGroup.kt` |
| **8** | **Thống kê năng suất & Báo cáo** | Trực quan hóa dữ liệu bằng biểu đồ Donut Chart (tỷ lệ hoàn thành công việc), biểu đồ cột Bar Chart (điểm số từng thành viên), bảng xếp hạng gia đình (Leaderboard). | `StatisticsScreen` | `StatisticsScreen.kt`<br>`HomeViewModel.kt` |
| **9** | **Đa ngôn ngữ & Hệ thống UI/UX** | Hỗ trợ song ngữ Tiếng Việt & Tiếng Anh (chuyển đổi tức thì không cần khởi động lại app), cài đặt thông tin cá nhân, sao chép mã mời, hệ thống theme Glassmorphism. | `SettingsScreen`<br>`Theme` & Components | `strings.xml` (vi)<br>`strings.xml` (en)<br>`Glassmorphism.kt`<br>`Color.kt`<br>`Theme.kt` |

* **Bảo vệ dữ liệu cá nhân & Quyền riêng tư:** Cô lập dữ liệu tuyệt đối giữa các gia đình bằng khóa ngoại `familyId`; mã mời gia đình được mã hóa và bảo mật; chỉ thành viên trong cùng nhóm mới xem được thống kê nội bộ gia đình.

---

### 📋 Ma trận phân công trách nhiệm (RACI Matrix)

| Module / Nhiệm vụ chính | Lỗ Mạnh Trường | Phạm Nguyễn Dũng | Bùi Trung Kiên |
|:---|:---:|:---:|:---:|
| **Khung dự án (Architecture & DI & Room)** | **R** (Responsible) / **A** (Accountable) | **C** (Consulted) | **C** (Consulted) |
| **Hệ thống Design UI & Glassmorphism** | **C** (Consulted) | **C** (Consulted) | **R** / **A** |
| **Module 1: Xác thực & Phân quyền (Auth)** | **R** / **A** | **I** (Informed) | **C** (Consulted) |
| **Module 2: Quản lý & Phân công Task** | **R** / **A** | **C** (Consulted) | **I** (Informed) |
| **Module 3: Vòng đời Task & Duyệt ảnh** | **R** / **A** | **C** (Consulted) | **I** (Informed) |
| **Module 4: Lịch tuần & Kế hoạch (Calendar)** | **I** (Informed) | **R** / **A** | **C** (Consulted) |
| **Module 5: Dịch vụ Push Notification** | **C** (Consulted) | **R** / **A** | **I** (Informed) |
| **Module 6: Gamification & Đổi phần thưởng** | **C** (Consulted) | **R** / **A** | **I** (Informed) |
| **Module 7: Nhóm gia đình & Mã mời** | **C** (Consulted) | **I** (Informed) | **R** / **A** |
| **Module 8: Thống kê & Biểu đồ Canvas** | **I** (Informed) | **C** (Consulted) | **R** / **A** |
| **Module 9: Đa ngôn ngữ (i18n) & Cài đặt** | **I** (Informed) | **I** (Informed) | **R** / **A** |
| **Kiểm thử tích hợp & Báo cáo tổng thể** | **R** / **A** | **R** | **R** |

> *Ghi chú RACI:* **R** = Thực hiện chính (Responsible), **A** = Chịu trách nhiệm duyệt (Accountable), **C** = Tham vấn hỗ trợ (Consulted), **I** = Nhận thông tin (Informed).

---

## 📸 Ảnh chụp màn hình

| Màn hình | Mô tả |
|----------|-------|
| 🔐 **Login** | Đăng ký tên, email, chọn vai trò (Admin/Member), chọn avatar emoji |
| 👨‍👩‍👦 **Family Setup** | Tạo nhóm + sinh mã mời 6 số; hoặc Nhập mã mời để tham gia |
| 🏠 **Home** | Tab lọc task, filter thành viên, FAB tạo việc, Bottom Navigation |
| ➕ **Create Task** | Form tạo việc: tiêu đề, mô tả, giao cho ai, ưu tiên, điểm, hạn chót |
| 📋 **Task Detail** | Xem chi tiết, cập nhật trạng thái, tải ảnh minh chứng, emoji reactions |
| 🎁 **Rewards** | Danh sách phần thưởng, đổi bằng điểm, duyệt yêu cầu (Admin) |
| 📅 **Calendar** | Lịch tuần tương tác, dot indicator số task, xem task theo ngày |
| 📊 **Statistics** | Donut Chart %, Bar Chart điểm, Bảng xếp hạng, Phân công công việc |
| ⚙️ **Settings** | Đổi avatar, sao chép mã mời, chuyển ngôn ngữ, Đăng xuất |

---

## 🔒 Bảo vệ dữ liệu & Phân quyền

| Quyền | ADMIN | MEMBER |
|-------|-------|--------|
| Tạo nhiệm vụ | ✅ | ❌ |
| Giao việc cho người khác | ✅ | ❌ |
| Duyệt / Từ chối nhiệm vụ | ✅ | ❌ |
| Tạo phần thưởng | ✅ | ❌ |
| Duyệt yêu cầu đổi thưởng | ✅ | ❌ |
| Cập nhật trạng thái việc của mình | ✅ | ✅ |
| Tải ảnh minh chứng | ✅ | ✅ |
| Xem danh sách gia đình | ✅ | ✅ |
| Đổi thưởng bằng điểm | ❌ | ✅ |

---

## 🧪 Kiểm thử

| Loại Test | Framework | Trạng thái |
|-----------|-----------|------------|
| Unit Test ViewModel | JUnit 4 | ✅ Configured |
| UI Test | Espresso + Compose Test | ✅ Configured |
| Integration Test | Room In-Memory DB | Tương lai |

---

## 📄 Giấy phép

Dự án này được tạo phục vụ mục đích học tập trong khuôn khổ môn học **"Phát triển ứng dụng cho các thiết bị di động"** tại **Học viện Công nghệ Bưu chính Viễn thông (PTIT)**.

---

<div align="center">

Made with ❤️ by **Nhóm D22 — PTIT**  
**Lỗ Mạnh Trường • Phạm Nguyễn Dũng • Bùi Trung Kiên**

_"Gia đình gắn kết, việc nhà có tổ chức"_

</div>
