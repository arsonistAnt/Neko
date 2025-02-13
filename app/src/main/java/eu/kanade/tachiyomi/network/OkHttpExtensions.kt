package eu.kanade.tachiyomi.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter = object : AtomicBoolean(), Producer, Subscription {
            override fun request(n: Long) {
                if (n == 0L || !compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (error: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(error)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled()
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP error ${response.code}")
        }
    }
}

fun OkHttpClient.newCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
            .cache(null)
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                        .body(ProgressResponseBody(originalResponse.body!!, listener))
                        .build()
            }
            .build()

    return progressClient.newCall(request)
}

fun Response.consumeBody(): String? {
    use {
        if (it.code != 200) throw Exception("HTTP error ${it.code}")
        return it.body?.string()
    }
}

fun Response.consumeXmlBody(): String? {
    use { res ->
        if (res.code != 200) throw Exception("Export list error")
        BufferedReader(InputStreamReader(GZIPInputStream(res.body?.source()?.inputStream()))).use { reader ->
            val sb = StringBuilder()
            reader.forEachLine { line ->
                sb.append(line)
            }
            return sb.toString()
        }
    }
}