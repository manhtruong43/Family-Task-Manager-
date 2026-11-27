package com.family.taskmanager.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Danh sách migration của cơ sở dữ liệu - giữ dữ liệu người dùng khi nâng cấp phiên bản DB.
 *
 * Quy ước của nhóm (từ phiên bản 4 trở đi):
 *  1. Muốn đổi cấu trúc bảng (thêm bảng / thêm cột...) -> tăng `version` trong AppDatabase lên 1.
 *  2. Viết 1 Migration tương ứng ở đây và thêm vào [ALL].
 *  3. Build lại -> Room sinh file schema mới trong app/schemas/ -> commit file đó cùng code.
 *
 * Nếu quên bước 2, app sẽ dừng với lỗi "A migration from X to Y was required" ngay khi mở,
 * thay vì âm thầm XÓA SẠCH dữ liệu như trước (fallbackToDestructiveMigration).
 *
 * Các migration được đăng ký trong [ALL] và kiểm tra bằng fixture schema cũ.
 */
object AppMigrations {

    /** Các phiên bản cũ thời còn phát triển (1-3) không giữ dữ liệu: cho phép xóa và tạo lại. */
    val LEGACY_DEV_VERSIONS = intArrayOf(1, 2, 3)

    /**
     * 4 -> 5: thêm cột openToAll cho bảng tasks (việc "ai làm cũng được").
     * Việc cũ đang giao cho cả nhà (chưa ai nhận) được đánh dấu openToAll = 1 để giữ đúng ý nghĩa.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN openToAll INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE tasks SET openToAll = 1 WHERE assignedToUserId = ''")
        }
    }

    /**
     * 5 -> 6:
     * - Thêm cột relationLabel cho bảng users (nhãn Bố/Mẹ độc lập với avatar).
     * - Thêm cột submissionRevision cho bảng tasks (đếm phiên bản từng lượt nộp minh chứng).
     *   Các nhiệm vụ cũ đang ở trạng thái chờ duyệt (PENDING_APPROVAL) được đặt submissionRevision = 1.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN relationLabel TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tasks ADD COLUMN submissionRevision INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE tasks SET submissionRevision = 1 WHERE status = 'PENDING_APPROVAL' AND submissionRevision = 0")
        }
    }

    /** 6 -> 7: lịch cũ giữ nguyên một việc chung, không sinh thêm việc riêng ngoài ý muốn. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recurring_schedules ADD COLUMN assignmentMode TEXT NOT NULL DEFAULT 'SHARED'")
        }
    }

    /** 7 -> 8: chỉ thêm biên nhận, không thay lịch, tiến độ, điểm hay dấu kỳ đã sinh. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS recurring_creation_requests (" +
                    "requestId TEXT NOT NULL, actorId TEXT NOT NULL, familyId TEXT NOT NULL, " +
                    "scheduleId TEXT NOT NULL, PRIMARY KEY(requestId), " +
                    "FOREIGN KEY(familyId) REFERENCES family_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_recurring_creation_requests_familyId " +
                    "ON recurring_creation_requests(familyId)"
            )
        }
    }

    /**
     * 8 -> 9:
     * - Thêm chế độ việc linh hoạt trong tuần (taskMode, targetCount, completedCount) cho bảng tasks.
     * - Thêm thời gian nhắc trước hạn chót (reminderLeadMinutes: 30 / 60 / 120) cho tasks và recurring_schedules.
     *   Toàn bộ dữ liệu cũ được giữ nguyên với giá trị mặc định tương thích ngược.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN taskMode TEXT NOT NULL DEFAULT 'FIXED'")
            db.execSQL("ALTER TABLE tasks ADD COLUMN targetCount INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE tasks ADD COLUMN completedCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminderLeadMinutes INTEGER NOT NULL DEFAULT 30")
            db.execSQL("ALTER TABLE recurring_schedules ADD COLUMN reminderLeadMinutes INTEGER NOT NULL DEFAULT 30")
        }
    }

    /**
     * 9 -> 10: giữ biên nhận điểm sau khi sửa/hủy nhiệm vụ hoặc thành viên rời nhóm.
     * Không cộng lại vào users.points. DB 9 không có lịch sử điểm từng lượt, nên bản
     * ghi linh hoạt cũ được đánh dấu ước tính; không giả định đây là số liệu chính xác.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS task_approvals (" +
                    "taskId TEXT NOT NULL, submissionRevision INTEGER NOT NULL, familyId TEXT NOT NULL, " +
                    "userId TEXT NOT NULL, points INTEGER NOT NULL, taskCreatedAt INTEGER NOT NULL, " +
                    "approvedAt INTEGER NOT NULL, approvedRounds INTEGER NOT NULL DEFAULT 1, " +
                    "legacyEstimate INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(taskId, submissionRevision))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_task_approvals_familyId ON task_approvals(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_task_approvals_userId ON task_approvals(userId)")
            // -1 là biên nhận gộp dữ liệu cũ, không đụng phiên bản các lượt nộp mới.
            db.execSQL(
                "INSERT INTO task_approvals " +
                    "(taskId, submissionRevision, familyId, userId, points, taskCreatedAt, approvedAt, approvedRounds, legacyEstimate) " +
                    "SELECT id, -1, familyId, CASE WHEN taskMode = 'FLEXIBLE_WEEKLY' AND openToAll = 1 THEN '' ELSE assignedToUserId END, " +
                    "CASE WHEN taskMode = 'FLEXIBLE_WEEKLY' THEN points * completedCount ELSE points END, " +
                    "createdAt, createdAt, CASE WHEN taskMode = 'FLEXIBLE_WEEKLY' THEN completedCount ELSE 1 END, " +
                    "CASE WHEN taskMode = 'FLEXIBLE_WEEKLY' THEN 1 ELSE 0 END " +
                    "FROM tasks WHERE (taskMode = 'FIXED' AND status = 'COMPLETED') " +
                    "OR (taskMode = 'FLEXIBLE_WEEKLY' AND completedCount > 0)"
            )
            // Việc linh hoạt chung ở bản thử nghiệm 9 không xác định được tiến độ của từng người.
            // Giữ biên nhận cũ bên trên; phần chưa hoàn thành bắt đầu lại thành mục tiêu cá nhân.
            db.execSQL(
                "UPDATE tasks SET completedCount = 0, " +
                    "openToAll = CASE WHEN assignedToUserId = '' THEN 1 ELSE 0 END " +
                    "WHERE taskMode = 'FLEXIBLE_WEEKLY' AND openToAll = 1 AND status != 'COMPLETED'"
            )
        }
    }

    /** 10 -> 11: biên nhận nhắc và hộp thư nguyên tử; tách các lần giao việc. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminderGeneration INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE task_approvals ADD COLUMN workerName TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "UPDATE task_approvals SET workerName = COALESCE(" +
                    "(SELECT name FROM users WHERE users.id = task_approvals.userId), '')"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS deadline_reminder_receipts (" +
                    "taskId TEXT NOT NULL, deadlineTimestamp INTEGER NOT NULL, generation INTEGER NOT NULL, " +
                    "stageKey TEXT NOT NULL, PRIMARY KEY(taskId, deadlineTimestamp, generation, stageKey), " +
                    "FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            // Việc linh hoạt đang chờ người nhận là mục tiêu đã trả về nhóm ở bản cũ.
            db.execSQL(
                "UPDATE tasks SET reminderGeneration = 1 WHERE taskMode = 'FLEXIBLE_WEEKLY' " +
                    "AND assignedToUserId = '' AND status != 'COMPLETED'"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11
    )
}
