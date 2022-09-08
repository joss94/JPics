package fr.curlyspiker.jpics

import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URLEncoder


object PiwigoServerHelper {

    var serverUrl = ""
    private lateinit var requestQueue: RequestQueue

    fun initialize(queue: RequestQueue) {
        if(!this::requestQueue.isInitialized) {
            CookieHandler.setDefault(CookieManager())
            requestQueue = queue
        }
    }

    fun volleyPost(params: JSONObject, cb : (JSONObject) -> Unit) {
        val url = "${serverUrl}/ws.php?format=json"
        val req = object: StringRequest(
            Method.POST, url,
            { response ->
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val rsp = JSONObject(response)
                        if (rsp.optString("stat", "error") == "ok") {
                            try {
                                val result = rsp.getJSONObject("result")
                                cb(result)
                            } catch (e: JSONException) {
                                cb(rsp)
                                Log.d("PSH", e.toString())
                                Log.d("PSH", params.toString())
                            }
                        }
                        else {
                            cb(rsp)
                        }
                    } catch (e: JSONException) {
                        cb(JSONObject())
                        Log.d("PSH", "Problem parsing response: $response")
                        Log.d("PSH", e.toString())
                    }
                }
            },
            { error ->
                //Log.d("JP", "Error in POST request (req: ${String(error.networkResponse.data, Charsets.UTF_8)})")
                cb(JSONObject())
                error.printStackTrace()
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
    }
}