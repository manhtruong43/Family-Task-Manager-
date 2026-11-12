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
 * Ví dụ khi thêm cột "category" cho bảng tasks ở phiên bản 5:
 *
 *     val MIGRATION_4_5 = object : Migration(4, 5) {
 *         override fun migrate(db: SupportSQLiteDatabase) {
 *             db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT ''")
 *         }
 *     }
 *     val ALL: Array<Migration> = arrayOf(MIGRATION_4_5)
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

    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
