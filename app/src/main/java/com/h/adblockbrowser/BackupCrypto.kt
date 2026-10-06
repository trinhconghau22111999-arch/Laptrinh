package com.h.adblockbrowser

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mã hoá / giải mã tệp sao lưu bằng MẬT KHẨU người dùng đặt (module thuần Kotlin/JVM, không dùng
 * API Android nên chạy thử được độc lập).
 *
 * Vì sao phải mã hoá: bản sao lưu chứa PHIÊN ĐĂNG NHẬP (cookie) của các tài khoản - ai có tệp
 * chưa mã hoá là vào được tài khoản. Tệp lại hay được gửi qua Zalo/Drive/Bluetooth nên bắt buộc
 * khoá bằng mật khẩu.
 *
 * Định dạng tệp:
 *   "LPBK1" (5 byte) | salt (16) | ivPrefix (4) | { độ dài khối (4 byte, big-endian) | dữ liệu mã hoá + tag GCM }...
 *  - Khoá AES-256 sinh từ mật khẩu bằng PBKDF2-HMAC-SHA1 (có trên MỌI bản Android, kể cả 7.0),
 *    [PBKDF2_ITERATIONS] vòng.
 *  - Dữ liệu cắt thành khối [CHUNK_SIZE]; mỗi khối mã hoá riêng bằng AES-GCM (có xác thực), IV =
 *    ivPrefix + số thứ tự khối nên KHÔNG BAO GIỜ trùng IV. AAD gắn số thứ tự + cờ "khối cuối" nên
 *    không thể đảo khối, bỏ khối hay cắt cụt tệp mà không bị phát hiện.
 *  - Mật khẩu sai hoặc tệp bị sửa: khối đầu tiên giải mã lỗi -> [WrongPasswordException]; lỗi ở
 *    khối sau -> [CorruptBackupException].
 */
object BackupCrypto {

    private val MAGIC = "LPBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_PREFIX_LEN = 4
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    const val CHUNK_SIZE = 256 * 1024
    const val PBKDF2_ITERATIONS = 250_000

    class WrongPasswordException : Exception("Sai mật khẩu hoặc tệp không đúng định dạng")
    class CorruptBackupException(message: String) : Exception(message)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        try {
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun chunkIv(prefix: ByteArray, index: Long): ByteArray {
        val iv = ByteArray(12)
        System.arraycopy(prefix, 0, iv, 0, IV_PREFIX_LEN)
        for (i in 0 until 8) iv[IV_PREFIX_LEN + i] = (index shr (56 - 8 * i)).toByte()
        return iv
    }

    private fun chunkAad(index: Long, last: Boolean): ByteArray {
        val aad = ByteArray(9)
        for (i in 0 until 8) aad[i] = (index shr (56 - 8 * i)).toByte()
        aad[8] = if (last) 1 else 0
        return aad
    }

    /** Bọc [out]: ghi tiêu đề rồi mã hoá mọi dữ liệu ghi vào luồng trả về. PHẢI close() luồng trả về. */
    fun encryptingStream(out: OutputStream, password: CharArray): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val ivPrefix = ByteArray(IV_PREFIX_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        out.write(MAGIC)
        out.write(salt)
        out.write(ivPrefix)
        return GcmChunkOutputStream(out, key, ivPrefix)
    }

    /** Bọc [input]: đọc tiêu đề rồi trả về luồng giải mã. Đọc hết luồng này = đã xác thực toàn bộ tệp. */
    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(MAGIC.size + SALT_LEN + IV_PREFIX_LEN)
        readFully(input, header, header.size) ?: throw CorruptBackupException("Tệp quá ngắn, không phải bản sao lưu")
        for (i in MAGIC.indices) {
            if (header[i] != MAGIC[i]) throw CorruptBackupException("Không phải tệp sao lưu của ứng dụng này")
        }
        val salt = header.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val ivPrefix = header.copyOfRange(MAGIC.size + SALT_LEN, header.size)
        val key = deriveKey(password, salt)
        return GcmChunkInputStream(input, key, ivPrefix)
    }

    /** Đọc đủ [len] byte. Trả null nếu hết luồng giữa chừng. */
    private fun readFully(input: InputStream, buf: ByteArray, len: Int): Unit? {
        var got = 0
        while (got < len) {
            val n = input.read(buf, got, len - got)
            if (n < 0) return null
            got += n
        }
        return Unit
    }

    private class GcmChunkOutputStream(
        private val out: OutputStream,
        private val key: SecretKey,
        private val ivPrefix: ByteArray
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK_SIZE)
        private var len = 0
        private var index = 0L
        private var closed = false

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, n: Int) {
            var o = off
            var remaining = n
            while (remaining > 0) {
                // Chỉ đẩy khối đầy đi khi CÒN dữ liệu mới tới - nhờ vậy khi close() luôn còn lại
                // khối cuối để đánh dấu "khối cuối" (chống cắt cụt tệp).
                if (len == CHUNK_SIZE) flushChunk(last = false)
                val c = minOf(remaining, CHUNK_SIZE - len)
                System.arraycopy(b, o, buf, len, c)
                len += c
                o += c
                remaining -= c
            }
        }

        private fun flushChunk(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, chunkIv(ivPrefix, index)))
            cipher.updateAAD(chunkAad(index, last))
            val ct = cipher.doFinal(buf, 0, len)
            out.write(byteArrayOf((ct.size ushr 24).toByte(), (ct.size ushr 16).toByte(), (ct.size ushr 8).toByte(), ct.size.toByte()))
            out.write(ct)
            index++
            len = 0
        }

        override fun flush() {
            out.flush()
        }

        override fun close() {
            if (closed) return
            closed = true
            flushChunk(last = true)
            out.flush()
            out.close()
        }
    }

    private class GcmChunkInputStream(
        input: InputStream,
        private val key: SecretKey,
        private val ivPrefix: ByteArray
    ) : InputStream() {
        private val inp = PushbackInputStream(input, 1)
        private var current = ByteArray(0)
        private var pos = 0
        private var index = 0L
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= current.size) {
                if (finished) return -1
                loadNextChunk()
            }
            val c = minOf(len, current.size - pos)
            System.arraycopy(current, pos, b, off, c)
            pos += c
            return c
        }

        private fun loadNextChunk() {
            val lenBytes = ByteArray(4)
            readFully(inp, lenBytes, 4) ?: throw CorruptBackupException("Tệp bị cắt cụt")
            val ctLen = ((lenBytes[0].toInt() and 0xFF) shl 24) or ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or (lenBytes[3].toInt() and 0xFF)
            if (ctLen < TAG_BYTES || ctLen > CHUNK_SIZE + TAG_BYTES) throw CorruptBackupException("Tệp bị hỏng")
            val ct = ByteArray(ctLen)
            readFully(inp, ct, ctLen) ?: throw CorruptBackupException("Tệp bị cắt cụt")
            val peek = inp.read()
            val last = peek == -1
            if (!last) inp.unread(peek)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, chunkIv(ivPrefix, index)))
                cipher.updateAAD(chunkAad(index, last))
                current = cipher.doFinal(ct)
            } catch (e: BadPaddingException) {
                // Khối ĐẦU lỗi gần như chắc chắn là sai mật khẩu; khối sau lỗi = tệp bị sửa/hỏng.
                if (index == 0L) throw WrongPasswordException()
                throw CorruptBackupException("Tệp sao lưu bị hỏng hoặc đã bị chỉnh sửa")
            }
            pos = 0
            index++
            if (last) finished = true
        }

        override fun close() {
            inp.close()
        }
    }
}
