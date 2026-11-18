package com.tvlauncher.data

import android.content.Context
import android.provider.Settings
import com.tvlauncher.BuildConfig
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Calendar

/**
 * ترخيص بلا خادم: مفتاح مرتبط برمز الجهاز يُولَّد عند البائع بنفس السر.
 * ملاحظة: أي ترخيص يعمل داخل الجهاز يمكن كسره بجهد كافٍ؛ الهدف منع الاستعمال العادي غير المرخّص.
 */
object License {
    const val TRIAL_DAYS = 7
    /** وسيلة التواصل التي تظهر للمستخدم بعد انتهاء التجربة (عدّلها قبل التوزيع). */
    const val SELLER_CONTACT = ""

    enum class State { OPEN, TRIAL, LICENSED, EXPIRED }
    data class Status(val state: State, val daysLeft: Int = 0, val until: String = "")

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun secret(): ByteArray? {
        val h = BuildConfig.LICENSE_X
        if (h.isEmpty()) return null
        val mask = "ZEM".toByteArray()
        return ByteArray(h.length / 2) { i -> (h.substring(i * 2, i * 2 + 2).toInt(16) xor mask[i % 3].toInt()).toByte() }
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("lic", 0)

    /** رمز الجهاز الذي يرسله المستخدم للبائع. */
    fun deviceCode(ctx: Context): String {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val d = MessageDigest.getInstance("SHA-256").digest((id + "|zl1").toByteArray())
        val hex = d.joinToString("") { "%02X".format(it) }.take(8)
        return hex.substring(0, 4) + "-" + hex.substring(4)
    }

    private fun mac(secret: ByteArray, device: String, tag: String): String {
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(secret, "HmacSHA256"))
        val b = m.doFinal("$device|$tag".toByteArray())
        val sb = StringBuilder()
        for (i in 0 until 16) {
            val pos = i * 5
            val v = (((b[pos / 8].toInt() and 0xFF) shl 8) or (b[pos / 8 + 1].toInt() and 0xFF)) shr (16 - 5 - pos % 8) and 31
            sb.append(ALPHABET[v])
        }
        return sb.toString()
    }

    /** يعيد تاريخ الانتهاء ("LIFE" = دائم) إن كان المفتاح صحيحاً لهذا الجهاز، وإلا null. */
    private fun check(ctx: Context, rawKey: String): String? {
        val s = secret() ?: return "LIFE"
        val k = rawKey.uppercase().filter { it.isLetterOrDigit() }
        if (k.length != 20) return null
        val tag = k.substring(0, 4); val sig = k.substring(4)
        val dev = deviceCode(ctx).replace("-", "")
        if (!MessageDigest.isEqual(mac(s, dev, tag).toByteArray(), sig.toByteArray())) return null
        return tag
    }

    fun activate(ctx: Context, rawKey: String): Boolean {
        val tag = check(ctx, rawKey) ?: return false
        if (expired(ctx, tag)) return false
        sp(ctx).edit().putString("key", rawKey.uppercase().filter { it.isLetterOrDigit() }).apply()
        return true
    }

    /** الوقت الحالي مع حماية بسيطة من إرجاع ساعة الجهاز للخلف. */
    private fun now(ctx: Context): Long {
        val p = sp(ctx); val t = maxOf(System.currentTimeMillis(), p.getLong("seen", 0L))
        p.edit().putLong("seen", t).apply(); return t
    }

    private fun monthEnd(tag: String): Long? {
        val yy = tag.substring(0, 2).toIntOrNull() ?: return null; val mm = tag.substring(2).toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        return Calendar.getInstance().apply { clear(); set(2000 + yy, mm - 1, 1); add(Calendar.MONTH, 1) }.timeInMillis
    }

    private fun expired(ctx: Context, tag: String): Boolean = tag != "LIFE" && (monthEnd(tag)?.let { now(ctx) >= it } ?: true)

    private fun trialStart(ctx: Context): Long {
        val f = File(ctx.filesDir, ".zl")
        val a = sp(ctx).getLong("t0", 0L)
        val b = runCatching { f.readText().trim().toLong() }.getOrDefault(0L)
        val t = listOf(a, b).filter { it > 0 }.minOrNull() ?: now(ctx)
        if (a == 0L) sp(ctx).edit().putLong("t0", t).apply()
        if (b == 0L) runCatching { f.writeText(t.toString()) }
        return t
    }

    fun status(ctx: Context): Status {
        if (secret() == null) return Status(State.OPEN)
        val key = sp(ctx).getString("key", null)
        if (key != null) {
            val tag = check(ctx, key)
            if (tag != null && !expired(ctx, tag)) return Status(State.LICENSED, until = if (tag == "LIFE") "دائم" else "حتى نهاية 20${tag.substring(0, 2)}/${tag.substring(2)}")
        }
        val left = TRIAL_DAYS - ((now(ctx) - trialStart(ctx)) / 86_400_000L).toInt()
        return if (left > 0) Status(State.TRIAL, daysLeft = left) else Status(State.EXPIRED)
    }
}
