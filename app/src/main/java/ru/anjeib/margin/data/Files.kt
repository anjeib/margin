package ru.anjeib.margin.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/** Attachment files live in filesDir/att; the JSON only stores their names. */
object Files {
    fun dir(ctx: Context) = File(ctx.filesDir, "att").apply { mkdirs() }
    fun file(ctx: Context, a: Attachment) = a.file?.let { File(dir(ctx), it) }

    /** Copies a picked content Uri into app storage. */
    suspend fun import(ctx: Context, uri: Uri, kindHint: String? = null): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val mime = cr.getType(uri)
            var name = "file"
            var size = 0L
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    size = c.getLong(1)
                }
            }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: name.substringAfterLast('.', "bin")
            val id = newId()
            val out = File(dir(ctx), "$id.$ext")
            cr.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            val kind = kindHint ?: when {
                mime?.startsWith("image/") == true -> "photo"
                mime?.startsWith("video/") == true -> "video"
                mime?.startsWith("audio/") == true -> "audio"
                else -> "file"
            }
            Attachment(id = id, kind = kind, file = out.name, name = name, mime = mime, size = if (size > 0) size else out.length(),
                duration = if (kind == "video" || kind == "audio") durationOf(out) else 0)
        }.getOrNull()
    }

    fun newPhotoFile(ctx: Context): Pair<File, Uri> {
        val f = File(dir(ctx), newId() + ".jpg")
        return f to FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    fun fromCamera(f: File): Attachment = Attachment(id = f.nameWithoutExtension, kind = "photo", file = f.name, name = f.name, mime = "image/jpeg", size = f.length())

    private fun durationOf(f: File): Int = runCatching {
        MediaMetadataRetriever().run {
            setDataSource(f.absolutePath)
            val ms = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            release(); (ms / 1000).toInt()
        }
    }.getOrDefault(0)

    /** Reads name/phone/email of a contact picked via ACTION_PICK (no permission needed for the picked row). */
    fun readContact(ctx: Context, uri: Uri): ContactInfo? = runCatching {
        val cr = ctx.contentResolver
        var name: String? = null; var phone: String? = null; var contactId: String? = null
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER).takeIf { it >= 0 }?.let { phone = c.getString(it) }
                c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID).takeIf { it >= 0 }?.let { contactId = c.getString(it) }
            }
        }
        val email = contactId?.let { id ->
            runCatching {
                cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?", arrayOf(id), null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
        }
        name?.let { ContactInfo(it, phone, email) }
    }.getOrNull()

    /** Small bitmap for previews (photos and video frames). */
    suspend fun thumb(ctx: Context, a: Attachment, px: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(ctx, a) ?: return@withContext null
        runCatching {
            when (a.kind) {
                "video" -> MediaMetadataRetriever().run {
                    setDataSource(f.absolutePath); val b = getFrameAtTime(0); release(); b?.let { scale(it, px) }
                }
                "photo" -> {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, o)
                    var sample = 1
                    while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2
                    BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun scale(b: Bitmap, px: Int): Bitmap {
        val k = px.toFloat() / maxOf(b.width, b.height)
        return if (k >= 1) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }

    /** JPEG data URL (≤1024 px) to show a photo to a vision model. */
    suspend fun imageDataUrl(ctx: Context, a: Attachment): String? = withContext(Dispatchers.IO) {
        val b = thumb(ctx, a, 1024) ?: return@withContext null
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 82, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** A photo as JPEG bytes, scaled so text stays readable but the upload stays small. */
    suspend fun jpeg(ctx: Context, a: Attachment, px: Int = 1600): ByteArray? = withContext(Dispatchers.IO) {
        val b = thumb(ctx, a, px) ?: return@withContext null
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    fun openIntent(ctx: Context, a: Attachment): Intent? {
        val f = file(ctx, a) ?: return null
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun delete(ctx: Context, a: Attachment) { file(ctx, a)?.delete() }
}

/** Records 16 kHz mono PCM into a WAV file — the format every audio model accepts. */
class WavRecorder(private val ctx: Context) {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var level: Float = 0f; private set
    private var out: File? = null
    private var startedAt = 0L

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4) }.getOrNull()
            ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        val f = File(Files.dir(ctx), newId() + ".wav")
        out = f; rec = r; running = true; startedAt = System.currentTimeMillis()
        r.startRecording()
        thread = Thread {
            RandomAccessFile(f, "rw").use { raf ->
                raf.write(ByteArray(44)) // header placeholder
                val buf = ShortArray(min)
                val bytes = ByteArray(min * 2)
                var total = 0L
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var peak = 0
                    for (i in 0 until n) {
                        val v = buf[i].toInt()
                        bytes[i * 2] = (v and 0xff).toByte(); bytes[i * 2 + 1] = (v shr 8 and 0xff).toByte()
                        peak = maxOf(peak, abs(v))
                    }
                    level = (peak / 32768f).coerceIn(0f, 1f)
                    raf.write(bytes, 0, n * 2); total += n * 2
                }
                raf.seek(0); raf.write(wavHeader(total, rate))
            }
        }.also { it.start() }
        return true
    }

    /** Stops and returns the attachment, or null when nothing useful was recorded. */
    fun stop(): Attachment? {
        running = false
        thread?.join(1500)
        rec?.runCatching { stop(); release() }
        rec = null
        val f = out ?: return null
        val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        if (f.length() < 16_000) { f.delete(); return null } // < ~0.5 s
        return Attachment(id = f.nameWithoutExtension, kind = "audio", file = f.name, name = f.name, mime = "audio/wav", size = f.length(), duration = secs)
    }

    fun cancel() { stop()?.let { Files.delete(ctx, it) } }

    private fun wavHeader(dataLen: Long, rate: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataLen).toInt()); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(rate); b.putInt(rate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataLen.toInt())
        return b.array()
    }
}

fun Attachment.durationLabel(): String = "%d:%02d".format(duration / 60, duration % 60)
