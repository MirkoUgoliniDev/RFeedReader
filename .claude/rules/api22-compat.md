---
paths:
  - "app/src/**/*.kt"
  - "app/build.gradle.kts"
  - "app/src/main/AndroidManifest.xml"
---

# Compatibilità Android 5.1 (API 22)

Il target reale è un Fire 7 con Android 5.1.1. Il compilatore NON segnala tutte le API troppo recenti: controllale tu.

## API da non usare senza guardia `Build.VERSION.SDK_INT`

| API | Livello | Alternativa per API 22 |
|---|---|---|
| `HttpURLConnection.contentLengthLong` / `getContentLengthLong()` | 24 | leggere l'header `Content-Length` e `toLongOrNull()` |
| `Html.fromHtml(String, Int)` | 24 | `@Suppress("DEPRECATION") Html.fromHtml(String)` |
| `Context.mainExecutor` | 28 | `ContextCompat.getMainExecutor(context)` |
| `Notification.Builder(context, channel)`, `NotificationChannel` | 26 | ramo `else` con il costruttore deprecato |
| `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` | 29 | `0` sotto Q |
| `java.time.*`, `java.util.stream` | 26/24 | ok solo grazie al desugaring (`desugar_jdk_libs`) |
| `Files`, `Path`, `java.nio.file` | 26 | **non** coperto dal desugaring: usare `java.io.File` |
| `logcat --pid`, `pidof` (shell) | 24/23 | `ps` + `grep` |

## Librerie

Non alzare versioni oltre il tetto indicato in `.claude/CLAUDE.md`. Per ogni aggiornamento usare la skill
`bump-dependency`: l'unica fonte autorevole del minSdk di una libreria è il manifest merger, non il POM.

## Rete e TLS su 5.1

- ISRG Root X1 (Let's Encrypt) non è nel trust store: ogni nuovo client HTTP deve usare `LegacyTrust`
  (`HttpsURLConnection` default socket factory è già installata in `Application.onCreate`; OkHttp va configurato a mano).
- Mai `TrustManager` permissivi, mai `HostnameVerifier` che restituisce sempre `true`.
- CPU lenta: evitare lavoro pesante su `Dispatchers.Main`, anche `File.canonicalFile` in liste lunghe.
