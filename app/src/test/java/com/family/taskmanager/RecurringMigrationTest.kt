package com.family.taskmanager

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.family.taskmanager.data.local.AppDatabase
import com.family.taskmanager.data.local.AppMigrations
import com.family.taskmanager.data.model.RecurringAssignmentMode
import com.family.taskmanager.data.model.TaskStatus
import com.family.taskmanager.data.repository.NotificationRepository
import com.family.taskmanager.data.repository.RecurringScheduleRepository
import com.family.taskmanager.data.service.NotificationPreferences
import com.family.taskmanager.data.service.PushNotificationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Fixture trên đĩa từ schema 6 đã commit; Room phải thực sự chạy migration và validate schema 7. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecurringMigrationTest {
    private lateinit var context: Context
    // Robolectric đã cô lập thư mục theo bài test; tên ngắn tránh giới hạn đường dẫn SQLite trên Windows.
    private val databaseName = "m67.db"
    private var room: AppDatabase? = null
    private val day = LocalDate.of(2026, 11, 12)
    private val family = "migration-family"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @After fun cleanup() {
        room?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppMigrations.ALL)
            .allowMainThreadQueries()
            .build().also { room = it }

    @Test fun migrate6to7_preservesOccurrences() = runBlocking<Unit> {
        createVersion6Fixture()
        assertTrue(context.getDatabasePath(databaseName).isFile)
        var db = openRoom()
        assertEquals(7, db.openHelper.writableDatabase.version) // upgrade + Room validation

        // Đóng/mở lại để kiểm tra cả identity/schema của DB đã được nâng cấp.
        db.close()
        room = null
        db = openRoom()
        assertEquals(7, db.openHelper.writableDatabase.version)
        val schedules = db.recurringScheduleDao().observeByFamily(family).first()
        assertEquals(2, schedules.size)
        assertTrue(schedules.all { it.assignmentMode == RecurringAssignmentMode.SHARED })
        assertTrue(schedules.all { it.lastGeneratedEpochDay == day.toEpochDay() })
        val dedicated = schedules.single { it.id == "legacy-na" }
        assertEquals("na", dedicated.assignedToUserId)
        assertEquals("Na", dedicated.assignedToUserName)
        assertEquals(17, dedicated.points)

        val oldSharedId = "rec_legacy-shared_" + day.toEpochDay()
        val oldDedicatedId = "rec_legacy-na_" + day.toEpochDay()
        val sharedProgress = db.taskDao().getTaskById(oldSharedId)!!
        val dedicatedProgress = db.taskDao().getTaskById(oldDedicatedId)!!
        assertEquals(TaskStatus.PENDING_APPROVAL, sharedProgress.status)
        assertEquals("shared-proof.jpg", sharedProgress.proofImageUrl)
        assertEquals(2, sharedProgress.submissionRevision)
        assertEquals(TaskStatus.IN_PROGRESS, dedicatedProgress.status)
        assertEquals(42, db.userDao().getUserByIdSync("na")!!.points)

        val preferences = NotificationPreferences(context).also { it.setEnabled(false) }
        val repository = RecurringScheduleRepository(
            context, db, NotificationRepository(db.notificationDao(), PushNotificationService(context, preferences))
        )
        repository.clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(0, repository.generateDueTasks())
        assertEquals(2, db.taskDao().getTasksByFamily(family).first().size)
        assertEquals(sharedProgress, db.taskDao().getTaskById(oldSharedId))
        assertEquals(dedicatedProgress, db.taskDao().getTaskById(oldDedicatedId))

        repository.clock = Clock.fixed(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(2, repository.generateDueTasks()) // Một việc chung + một việc giao Na, không mở rộng thành EACH_MEMBER.
        assertEquals(0, repository.generateDueTasks())
        val newTasks = db.taskDao().getTasksByFamily(family).first()
            .filter { it.id != oldSharedId && it.id != oldDedicatedId }
        assertEquals(2, newTasks.size)
        assertEquals(1, newTasks.count { it.openToAll && it.assignedToUserId.isEmpty() })
        assertEquals(1, newTasks.count { !it.openToAll && it.assignedToUserId == "na" })
        assertTrue(newTasks.all { it.status == TaskStatus.TODO && it.proofImageUrl.isEmpty() })
        assertEquals(sharedProgress, db.taskDao().getTaskById(oldSharedId))
        assertEquals(dedicatedProgress, db.taskDao().getTaskById(oldDedicatedId))
    }

    private fun createVersion6Fixture() {
        val input = javaClass.classLoader!!.getResourceAsStream(
            "com.family.taskmanager.data.local.AppDatabase/6.json"
        ) ?: error("Missing exported version-6 schema fixture")
        val schema = input.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        assertEquals(6, schema.getInt("version"))
        val file = context.getDatabasePath(databaseName)
        file.parentFile!!.mkdirs()
        assertTrue("Fixture directory unavailable: " + file.parentFile, file.parentFile!!.isDirectory)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace('$' + "{TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace('$' + "{TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.version = 6
            old.rawQuery("SELECT * FROM recurring_schedules", null).use {
                assertEquals(-1, it.getColumnIndex("assignmentMode"))
            }
            old.row("family_groups", "id" to family, "name" to "Nhà thử", "inviteCode" to "123456",
                "adminId" to "mom", "createdAt" to 1000L)
            for ((id, role) in listOf("mom" to "ADMIN", "dad" to "ADMIN", "na" to "MEMBER")) {
                old.row("users", "id" to id, "name" to if (id == "na") "Na" else id,
                    "email" to (id + "@example.test"), "avatarUrl" to "", "role" to role, "familyId" to family,
                    "points" to if (id == "na") 42 else 0, "isEmailVerified" to 1, "relationLabel" to "")
            }
            for ((id, assignee) in listOf("legacy-shared" to "", "legacy-na" to "na")) {
                old.row("recurring_schedules",
                    "id" to id, "familyId" to family, "title" to id, "description" to "Lịch v6",
                    "assignedToUserId" to assignee, "assignedToUserName" to if (assignee.isEmpty()) "" else "Na",
                    "priority" to "MEDIUM", "points" to 17, "repeatType" to "DAILY", "daysOfWeekMask" to 0,
                    "dueMinuteOfDay" to 1200, "isActive" to 1, "createdByUserId" to "mom", "createdAt" to 1000L,
                    "lastGeneratedEpochDay" to day.toEpochDay())
                val shared = assignee.isEmpty()
                old.row("tasks", "id" to ("rec_" + id + "_" + day.toEpochDay()), "title" to id,
                    "description" to "", "familyId" to family, "assignedToUserId" to "na", "assignedToUserName" to "Na",
                    "createdByUserId" to "mom", "priority" to "MEDIUM",
                    "status" to if (shared) "PENDING_APPROVAL" else "IN_PROGRESS", "points" to 17,
                    "deadlineTimestamp" to day.atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                    "proofImageUrl" to if (shared) "shared-proof.jpg" else "", "proofNote" to "",
                    "createdAt" to 1000L, "openToAll" to if (shared) 1 else 0, "submissionRevision" to if (shared) 2 else 0)
            }
        }
    }

    private fun SQLiteDatabase.row(table: String, vararg pairs: Pair<String, Any>) {
        val values = ContentValues()
        for ((key, value) in pairs) {
            when (value) {
                is String -> values.put(key, value)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                else -> error("Unsupported fixture type")
            }
        }
        insertOrThrow(table, null, values)
    }
}
