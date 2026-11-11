# Hai chế độ giao việc lặp lại — Dũng

## Cách dùng

Mở **Lịch → Lịch trình cố định → Thêm/Sửa lịch**.

- Chọn một người: giữ cách giao riêng hiện có.
- Chọn **Cả nhà → Một người nhận làm**: mỗi kỳ có một nhiệm vụ chung. Người hợp lệ nhận trước sẽ làm, nộp minh chứng và nhận điểm sau khi được duyệt.
- Chọn **Cả nhà → Mỗi người một việc riêng**: mỗi kỳ tạo một nhiệm vụ cho từng thành viên hợp lệ, trừ người giao. Mỗi nhiệm vụ có người nhận, trạng thái, minh chứng và điểm riêng. Phụ huynh chỉ được giao khi có phụ huynh khác để duyệt.
- Thành viên được lấy lại khi sinh mỗi kỳ, nên người đã rời nhóm không được giao việc mới; người mới vào được tính từ kỳ chưa sinh tiếp theo.
- Thay đổi lịch/chế độ không sửa, tách hay nhân đôi các nhiệm vụ đã sinh. Nếu kỳ hôm nay đã xử lý thì thay đổi áp dụng từ kỳ sau.
- Giao diện hiện vẫn chọn **một người hoặc Cả nhà**; chưa thêm danh sách chọn nhiều người cụ thể.
- Nếu không có ai hợp lệ ở chế độ mỗi người riêng, kỳ đó không tạo nhiệm vụ chung thay thế.

## Các phần được thay đổi

- Lưu thêm assignmentMode trong lịch; DB 6 → 7 giữ nguyên lịch, tiến độ và dấu kỳ đã sinh. Lịch cũ mặc định SHARED.
- ID nhiệm vụ riêng ổn định theo lịch + ngày + người nhận.
- Toàn bộ nhiệm vụ của một lịch trong một kỳ, inbox notification và dấu đã sinh được lưu trong cùng transaction.
- Giữ cơ chế nhận việc chung, kiểm tra quyền, duyệt và tính điểm của Trường.
- Bổ sung kiểm tra JVM cho quy tắc sinh việc và Android tests cho persistence/concurrency/rollback.

## Kiểm tra thủ công trên máy thật

Chuẩn bị trong cùng một bản cài thử: Mẹ là người tạo, Bố là phụ huynh khác, Na và Bin là thành viên. Tạo lịch có hạn sau giờ hiện tại để kỳ hôm nay được sinh; lịch tạo sau giờ hạn bắt đầu từ kỳ tiếp theo.

| Mã | Thao tác | Mong đợi |
| --- | --- | --- |
| R01 | Mẹ tạo lịch hằng ngày cho Cả nhà, chọn Một người nhận làm | Sinh đúng một nhiệm vụ chung; thẻ lịch ghi rõ chế độ |
| R02 | Na nhận việc R01, chuyển sang tài khoản Bin | Thấy Na đã nhận; Bin không nhận hoặc nộp thay được |
| R03 | Mẹ tạo lịch khác cho Cả nhà, chọn Mỗi người một việc riêng | Sinh ba nhiệm vụ cho Bố, Na, Bin; không sinh cho người giao là Mẹ |
| R04 | Na bắt đầu và nộp việc R03; mở việc của Bin | Việc của Bin vẫn chưa làm, không có ảnh/nội dung nộp của Na |
| R05 | Mẹ duyệt việc của Na; kiểm tra Bin và Bố | Chỉ Na được điểm của việc đã duyệt; các việc còn lại không tự hoàn thành |
| R06 | Mở lại Lịch nhiều lần, đóng/mở app | Không sinh thêm bộ nhiệm vụ của cùng kỳ và không mất tiến độ |
| R07 | Sửa lịch R01 sang mỗi người riêng sau khi kỳ hôm nay đã sinh | Việc hôm nay giữ nguyên; kỳ tiếp theo mới sinh các việc riêng |
| R08 | Lưu lịch cho riêng Bin, mở lại màn sửa rồi chỉ đổi tiêu đề | Vẫn giao Bin; sinh đúng một việc riêng; không tự chuyển sang Cả nhà |
| R09 | Tạo lịch hằng tuần, chọn đúng thứ hôm nay và hạn còn ở tương lai | Sinh đúng số việc theo chế độ; ngày không được chọn không sinh |
| R10 | Trước kỳ mới cho thành viên rời/cho người mới vào hoặc đổi tên | Kỳ mới dùng thành viên/tên mới nhất; không giao thêm cho người đã rời |
| R11 | Cài bản mới lên bản DB 6 đang có dữ liệu, không xóa dữ liệu app | Mở được app; dữ liệu cũ giữ nguyên; lịch cũ vẫn sinh như trước và không nhân đôi kỳ đã sinh |
| R12 | Xoay màn hình khi đang chọn chế độ hoặc lưu | Giữ lựa chọn; nút Lưu bị khóa khi đang xử lý; không sinh trùng bộ việc |

Trường hợp mỗi người riêng có phụ huynh duy nhất và người giao đã rời: người đó không nhận việc riêng mới cho chính mình; các con vẫn có thể nhận và được phụ huynh duyệt. Thực hiện rời nhóm theo điều kiện an toàn hiện có của ứng dụng.

## Phạm vi kiểm tra tự động

Lệnh kiểm tra không dùng máy ảo:

```powershell
.\gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests 'com.family.taskmanager.Recurring*' --tests com.family.taskmanager.TaskPermissionsTest --tests com.family.taskmanager.NotificationDeliveryTest --max-workers=2 --console=plain
```

Android tests mới chỉ được biên dịch khi không có thiết bị. Cần chạy trên bản cài thử hoặc thực hiện các bước trên để xác nhận Room/migration và giao diện lúc chạy; kết quả JVM không thay thế các kiểm tra đó.

Mẫu ghi kết quả: **R04 — Đạt/Không đạt; tài khoản; thao tác; mong đợi; thực tế; ảnh/video nếu gặp lỗi.**
