package com.family.taskmanager.data.local

import androidx.room.migration.Migration

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

    /** Hiện tại (phiên bản 4) chưa có migration nào. */
    val ALL: Array<Migration> = emptyArray()
}
