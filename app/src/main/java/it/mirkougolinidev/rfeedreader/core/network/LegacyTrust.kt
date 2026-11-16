package it.mirkougolinidev.rfeedreader.core.network

import android.content.Context
import android.os.Build
import it.mirkougolinidev.rfeedreader.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 5.1 non conosce ISRG Root X1: quella radice è entrata nel trust store solo con
 * Android 7.1.1 e la cross-firma DST Root CA X3 che teneva vivi i dispositivi più vecchi è
 * stata dismessa nel 2024. Senza questo innesto i feed e le immagini serviti da Let's Encrypt
 * (anchor.fm, le copertine di Radio 24) sono irraggiungibili.
 *
 * L'innesto è additivo: la verifica passa prima dal trust store di sistema e ricade sulla
 * radice imbarcata solo se il sistema la rifiuta. Nessun controllo viene disattivato.
 */
object LegacyTrust {

    /** Da Android 7.1.1 (API 25) ISRG Root X1 è già nel sistema e non serve fare nulla. */
    private val needed: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1

    @Volatile private var trustManager: X509TrustManager? = null
    @Volatile private var sslContext: SSLContext? = null

    /** Applica il trust combinato a tutte le connessioni HttpsURLConnection dell'app. */
    fun install(context: Context) {
        if (!needed) return
        runCatching {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext(context).socketFactory)
        }
    }

    fun sslContext(context: Context): SSLContext = sslContext ?: synchronized(this) {
        sslContext ?: SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager(context)), null)
        }.also { sslContext = it }
    }

    fun trustManager(context: Context): X509TrustManager = trustManager ?: synchronized(this) {
        trustManager ?: buildTrustManager(context).also { trustManager = it }
    }

    private fun buildTrustManager(context: Context): X509TrustManager {
        val system = systemTrustManager()
        if (!needed) return system
        val bundled = bundledTrustManager(context) ?: return system
        return CompositeTrustManager(listOf(system, bundled))
    }

    private fun systemTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    private fun bundledTrustManager(context: Context): X509TrustManager? = runCatching {
        val certificate = context.resources.openRawResource(R.raw.isrg_root_x1).use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificate(stream)
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("isrg-root-x1", certificate)
        }
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }.getOrNull()

    /** Accetta la catena se almeno uno dei trust manager la considera valida. */
    private class CompositeTrustManager(
        private val delegates: List<X509TrustManager>
    ) : X509TrustManager {

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            var failure: CertificateException? = null
            delegates.forEach { delegate ->
                try {
                    delegate.checkServerTrusted(chain, authType)
                    return
                } catch (error: CertificateException) {
                    failure = failure ?: error
                }
            }
            throw failure ?: CertificateException("Nessuna autorità di certificazione attendibile.")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegates.first().checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegates.flatMap { it.acceptedIssuers.asList() }.toTypedArray()
    }
}
