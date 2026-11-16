package app.olauncher.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the GitHub token for task sync encrypted with an AES-GCM key that never
 * leaves the Android Keystore. This is what EncryptedSharedPreferences does,
 * without its library, which is deprecated. The token is never logged or shown.
 */
object TokenStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "task_sync_token"
    private const val PREFS_FILENAME = "task_sync_secure"
    private const val TOKEN = "TOKEN"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** The token, or "" when none is saved or it cannot be read (for example after a restore). */
    fun get(context: Context): String = try {
        val stored = prefs(context).getString(TOKEN, null) ?: return ""
        val (iv, encrypted) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    /** Saves [token], or removes the saved one when it is blank. */
    fun set(context: Context, token: String) {
        if (token.isBlank()) {
            prefs(context).edit { remove(TOKEN) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs(context).edit { putString(TOKEN, stored) }
    }

    fun has(context: Context) = get(context).isNotEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
