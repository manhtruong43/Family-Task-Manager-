package com.family.taskmanager

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.family.taskmanager.data.repository.ProofImageStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Kiểm thử lưu ảnh minh chứng: ảnh được CHÉP vào bộ nhớ riêng của app nên vẫn đọc được
 * khi nguồn gốc (ảnh trong thư viện / file tạm) không còn.
 * Chạy trên máy ảo / máy thật: chuột phải file -> Run 'ProofImageStorageTest'.
 */
@RunWith(AndroidJUnit4::class)
class ProofImageStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var storage: ProofImageStorage
    private val imageBytes = ByteArray(2048) { (it % 251).toByte() }

    @Before
    fun setUp() {
        storage = ProofImageStorage(context)
    }

    /** Tạo 1 file "ảnh" giả trong thư mục cache (đóng vai ảnh người dùng chọn). */
    private fun sourceImage(name: String): File =
        File(context.cacheDir, name).apply { writeBytes(imageBytes) }

    @Test
    fun persist_copiesImage_intoAppStorage_andSurvivesSourceDeletion() = runBlocking<Unit> {
        val source = sourceImage("source.jpg")

        val saved = storage.persist("task-1", listOf(Uri.fromFile(source)))
        assertEquals(1, saved.size)

        // Ảnh gốc mất (vd: người dùng xóa ảnh trong thư viện) -> bản đã chép vẫn đọc được
        source.delete()
        val copy = File(Uri.parse(saved.single()).path!!)
        assertTrue(copy.exists())
        assertTrue(copy.canonicalPath.startsWith(File(context.filesDir, "proof_images").canonicalPath))
        assertArrayEquals(imageBytes, copy.readBytes())

        // Mở lại qua ContentResolver như Coil làm khi hiển thị ảnh
        val reopened = context.contentResolver.openInputStream(Uri.parse(saved.single()))!!.use { it.readBytes() }
        assertArrayEquals(imageBytes, reopened)
    }

    @Test
    fun persist_skipsBrokenSources_andRespectsLimit() = runBlocking<Unit> {
        val missing = Uri.fromFile(File(context.cacheDir, "khong-ton-tai.jpg"))
        val ok = (1..ProofImageStorage.MAX_IMAGES_PER_PROOF + 2).map { Uri.fromFile(sourceImage("img$it.jpg")) }

        assertEquals(0, storage.persist("task-2", listOf(missing)).size)
        assertEquals(ProofImageStorage.MAX_IMAGES_PER_PROOF, storage.persist("task-3", ok).size)
    }

    @Test
    fun delete_removesOnlyFilesInsideAppStorage() = runBlocking<Unit> {
        val saved = storage.persist("task-4", listOf(Uri.fromFile(sourceImage("del.jpg"))))
        val outside = sourceImage("ngoai-thu-muc.jpg")

        storage.delete(saved + Uri.fromFile(outside).toString())

        assertFalse(File(Uri.parse(saved.single()).path!!).exists())
        assertTrue(outside.exists())   // file ngoài thư mục ảnh minh chứng không bị đụng tới
    }
}
