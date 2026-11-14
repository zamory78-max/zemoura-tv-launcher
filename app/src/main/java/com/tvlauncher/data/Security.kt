package com.tvlauncher.data

import java.security.MessageDigest
import java.security.SecureRandom

/** تخزين الرمز السري كبصمة مملحة (لا يُحفظ الرمز نفسه). */
object Security {
    fun newSalt(): String {
        val b = ByteArray(16); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun hash(pin: String, salt: String): String {
        var d = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(10_000) { d = md.digest(d) }   // تكرار لإبطاء التخمين
        return d.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, salt: String, hash: String) = salt.isNotEmpty() && hash(pin, salt) == hash
}
