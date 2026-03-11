package com.aspect.braveyoutube.data.repository

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Custom HTTP Downloader for the NewPipe Extractor library.
 *
 * Modeled after the official NewPipe app's DownloaderImpl:
 * - Uses OkHttp instead of HttpURLConnection for better connection reuse
 * - Adds SOCS consent cookie to all YouTube requests (avoids "page needs to be reloaded")
 * - Uses Firefox User-Agent (matches real NewPipe app)
 * - Handles 429 ReCaptcha responses
 */
class NewPipeDownloader private constructor() : Downloader() {

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }

        private const val TAG = "NewPipeDownloader"

        // Same User-Agent the real NewPipe app uses (Firefox on Windows)
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        // SOCS consent cookie — tells YouTube consent was accepted
        // "CAISAiAD" = consent accepted; matches NewPipe's generateConsentCookie()
        private const val YOUTUBE_CONSENT_COOKIE = "SOCS=CAISAiAD"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        // Add cookie header for YouTube domains (consent cookie prevents "page reload" error)
        if (url.contains("youtube.com") || url.contains("googlevideo.com") ||
            url.contains("youtube-nocookie.com") || url.contains("googleapis.com")) {
            requestBuilder.addHeader("Cookie", YOUTUBE_CONSENT_COOKIE)
        }

        // Set request headers from the extractor
        headers?.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Set HTTP method and body
        val body = dataToSend?.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        when (httpMethod) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> requestBuilder.post(body ?: ByteArray(0).toRequestBody(null))
            "PUT" -> requestBuilder.put(body ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> {
                if (body != null) requestBuilder.delete(body)
                else requestBuilder.delete()
            }
            else -> requestBuilder.method(httpMethod, body)
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()
        val responseCode = okResponse.code

        // Handle ReCaptcha / rate limiting (429)
        if (responseCode == 429) {
            okResponse.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        // Read response headers
        val responseHeaders = mutableMapOf<String, List<String>>()
        for (name in okResponse.headers.names()) {
            responseHeaders[name] = okResponse.headers.values(name)
        }

        val responseBody = okResponse.body?.string() ?: ""
        val latentUrl = okResponse.request.url.toString()

        Log.d(TAG, "$httpMethod $responseCode ${url.take(100)}")

        return Response(
            responseCode,
            okResponse.message,
            responseHeaders,
            responseBody,
            latentUrl
        )
    }
}
