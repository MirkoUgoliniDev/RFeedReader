# Il trust manager viene istanziato solo via reflection dal provider JSSE: va preservato.
-keep class it.mirkougolinidev.rfeedreader.core.network.LegacyTrust { *; }

# OkHttp 4 su Android: riferimenti a classi opzionali assenti sul dispositivo.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
