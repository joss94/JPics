package fr.curlyspiker.jpics

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URLEncoder


object PiwigoServerHelper {

    var serverUrl = ""
    private lateinit var requestQueue: RequestQueue

    fun initialize(queue: RequestQueue) {
        CookieHandler.setDefault(CookieManager())
        requestQueue = queue
    }

    fun volleyGet(command: String, cb : (JSONObject) -> Unit) {
        val url = "https://www.curlyspiker.fr/photo/piwigo/ws.php?format=json&method=$command"
        val req = JsonObjectRequest (
            Request.Method.GET, url, null,
            { response ->
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        cb(response)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            },
            { error -> error.printStackTrace() }
        )

        requestQueue.add(req)
    }

    fun volleyPost(params: JSONObject, cb : (JSONObject) -> Unit) {
        val url = "https://www.curlyspiker.fr/photo/piwigo/ws.php?format=json"

        val req = object: StringRequest(
            Request.Method.POST, url,
            { response ->
                GlobalScope.launch(Dispatchers.IO) {
                    cb(JSONObject(response))
                }
            },
            { error ->
                Log.d("JP", "Error in POST request (req: ${String(error.networkResponse.data, Charsets.UTF_8)})")
                cb(JSONObject())
                error.printStackTrace()
            }
        ) {
            override fun getBody(): ByteArray {
                var content = ""
                params.keys().forEach { key ->
                    val obj = params[key]
                    if(obj is JSONArray) {
                        content += "&$key[]="
                        for(i in 0 until obj.length()) {
                            content += URLEncoder.encode(obj[i].toString(), "utf-8")
                            if(i < obj.length() - 1) {
                                content += ","
                            }
                        }
                    } else {
                        val txt = URLEncoder.encode(obj.toString(), "utf-8")
                        content += "&$key=$txt"
                    }
                }
                return content.toByteArray(Charsets.US_ASCII)
            }
        }


        Log.d("TAG", "Req map: ${String(req.body, Charsets.US_ASCII)}")
        requestQueue.add(req)
    }
}