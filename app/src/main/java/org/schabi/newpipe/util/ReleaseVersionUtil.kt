package org.schabi.newpipe.util

import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.core.content.pm.PackageInfoCompat
import org.schabi.newpipe.App
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.UserAction
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ReleaseVersionUtil {
    // Public key of the certificate that is used in NewPipe release versions
    private const val RELEASE_CERT_PUBLIC_KEY_SHA1 =
        "7F:46:0D:D0:6A:2D:A0:6B:57:B5:2C:ED:73:06:B7:87:43:90:66:A9"

    @JvmStatic
    fun isReleaseApk(): Boolean {
        return certificateSHA1Fingerprint == RELEASE_CERT_PUBLIC_KEY_SHA1
    }

    /**
     * Method to get the APK's SHA1 key. See https://stackoverflow.com/questions/9293019/#22506133.
     *
     * @return String with the APK's SHA1 fingerprint in hexadecimal
     */
    private val certificateSHA1Fingerprint: String
        get() {
            val app = App.getApp()
            val signatures: List<Signature> = try {
                PackageInfoCompat.getSignatures(app.packageManager, app.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                showRequestError(app, e, "Could not find package info")
                return ""
            }
            if (signatures.isEmpty()) {
                return ""
            }
            val x509cert = try {
                val cf = CertificateFactory.getInstance("X509")
                cf.generateCertificate(signatures[0].toByteArray().inputStream()) as X509Certificate
            } catch (e: CertificateException) {
                showRequestError(app, e, "Certificate error")
                return ""
            }

            return try {
                val md = MessageDigest.getInstance("SHA1")
                val publicKey = md.digest(x509cert.encoded)
                byte2HexFormatted(publicKey)
            } catch (e: NoSuchAlgorithmException) {
                showRequestError(app, e, "Could not retrieve SHA1 key")
                ""
            } catch (e: CertificateEncodingException) {
                showRequestError(app, e, "Could not retrieve SHA1 key")
                ""
            }
        }

    private fun byte2HexFormatted(arr: ByteArray): String {
        val str = StringBuilder(arr.size * 2)
        for (i in arr.indices) {
            var h = Integer.toHexString(arr[i].toInt())
            val l = h.length
            if (l == 1) {
                h = "0$h"
            }
            if (l > 2) {
                h = h.substring(l - 2, l)
            }
            str.append(h.uppercase())
            if (i < arr.size - 1) {
                str.append(':')
            }
        }
        return str.toString()
    }

    private fun showRequestError(app: App, e: Exception, request: String) {
        createNotification(
            app, ErrorInfo(e, UserAction.CHECK_FOR_NEW_APP_VERSION, request)
        )
    }

    fun isLastUpdateCheckExpired(expiry: Long): Boolean {
        return Instant.ofEpochSecond(expiry).isBefore(Instant.now())
    }

    /**
     * Coerce expiry date time in between 6 hours and 72 hours from now
     *
     * @return Epoch second of expiry date time
     */
    fun coerceUpdateCheckExpiry(expiryString: String?): Long {
        val now = ZonedDateTime.now()
        return expiryString?.let {
            var expiry =
                ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(expiryString))
            expiry = maxOf(expiry, now.plusHours(6))
            expiry = minOf(expiry, now.plusHours(72))
            expiry.toEpochSecond()
        } ?: now.plusHours(6).toEpochSecond()
    }
}
