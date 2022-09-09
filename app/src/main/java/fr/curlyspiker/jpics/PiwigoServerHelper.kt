package fr.curlyspiker.jpics

import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PiwigoException(private val msg: String): Exception(msg)

object PiwigoServerHelper {

    var serverUrl = ""
    private lateinit var requestQueue: RequestQueue

    fun initialize(queue: RequestQueue) {
        if(!this::requestQueue.isInitialized) {
            CookieHandler.setDefault(CookieManager())
            requestQueue = queue
        }
    }

    suspend fun volleyPost(params: JSONObject) : JSONObject = suspendCoroutine {
        try {
            Log.d("PSH", "New request: $params")
            val url = "${serverUrl}/ws.php?format=json"
            val req = object: StringRequest(
                Method.POST, url,
                { response ->
                    try {
                        val rsp = JSONObject(response)
                        if (rsp.optString("stat", "error") == "ok") {
                            try {
                                val result = rsp.getJSONObject("result")
                                it.resume(result)
                            } catch (e: JSONException) {
                                it.resume(rsp)
                            }
                        }
                        else {
                            Log.d("PSH", "Reply with stat different than OK")
                            it.resumeWithException(PiwigoException("Reply with stat different than OK"))
                        }
                    } catch (e: Exception) {
                        Log.d("PSH", "Problem parsing response: $response")
                        Log.d("PSH", e.toString())
                        it.resumeWithException(PiwigoException("Problem parsing response: $response"))
                    }
                },
                { error ->
                    Log.d("PSH", "Error in POST request (req: ${String(error.networkResponse.data, Charsets.UTF_8)})")
                    error.printStackTrace()
                    it.resumeWithException(PiwigoException("Error in POST request (req: ${String(error.networkResponse.data, Charsets.UTF_8)})"))
                }
            ) {
                override fun getBody(): ByteArray {
                    var content = ""
                    params.keys().forEach { key ->
                        val obj = params[key]
                        if(obj is JSONArray) {
                            for(i in 0 until obj.length()) {
                                content += "&$key[]="
                                content += URLEncoder.encode(obj[i].toString(), "utf-8")
                            }
                        } else {
                            val txt = URLEncoder.encode(obj.toString(), "utf-8")
                            content += "&$key=$txt"
                        }
                    }
                    return content.toByteArray(Charsets.US_ASCII)
                }
            }
            requestQueue.add(req)
        } catch(e: Exception){
            it.resumeWithException(PiwigoException(e.toString()))
        }
    }
}