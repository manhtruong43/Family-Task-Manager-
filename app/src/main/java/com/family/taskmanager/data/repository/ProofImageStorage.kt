package com.family.taskmanager.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lưu ảnh minh chứng vào bộ nhớ riêng của ứng dụng (filesDir/proof_images).
 *
 * Vì sao cần: bộ chọn ảnh chỉ trả về đường dẫn content:// với quyền đọc TẠM THỜI.
 * Nếu lưu thẳng đường dẫn đó vào DB thì sau khi tắt app mở lại, Bố/Mẹ có thể không xem
 * được ảnh nữa. Chép ảnh vào thư mục của app -> đường dẫn file:// dùng được mãi.
 */
@Singleton
class ProofImageStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DIRECTORY = "proof_images"
        const val MAX_IMAGES_PER_PROOF = 6
    }

    /**
     * Chép các ảnh đã chọn vào bộ nhớ của app.
     * @return danh sách đường dẫn file:// của các ảnh chép thành công (ảnh lỗi bị bỏ qua).
     */
    suspend fun persist(taskId: String, sources: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val safeTaskId = taskId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val batch = System.currentTimeMillis()

        sources.take(MAX_IMAGES_PER_PROOF).mapIndexedNotNull { index, source ->
            runCatching {
                val target = File(directory, "${safeTaskId}_${batch}_$index.${extensionOf(source)}")
                val input = context.contentResolver.openInputStream(source) ?: return@runCatching null
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
                if (target.length() == 0L) {
                    target.delete()
                    null
                } else {
                    Uri.fromFile(target).toString()
                }
            }.getOrNull()
        }
    }

    /** Xóa các ảnh cũ do app tự lưu (bỏ qua đường dẫn không thuộc thư mục của app). */
    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY).canonicalPath
        paths.forEach { path ->
            val file = Uri.parse(path).path?.let { File(it) } ?: return@forEach
            if (file.canonicalPath.startsWith(directory)) file.delete()
        }
    }

    private fun extensionOf(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
    }
}
