package fr.curlyspiker.jpics

import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.net.CookieHandler
import java.net.CookieManager

object PiwigoServerHelper {

    private lateinit var requestQueue: RequestQueue

    fun initialize(queue: RequestQueue) {
        // Use a cookie manager to handle automatically all cookies
        CookieHandler.setDefault(CookieManager())

        requestQueue = queue
    }

    fun volleyGet(command: String, cb : (JSONObject) -> Unit) {
        val url = "https://www.curlyspiker.fr/photo/piwigo/ws.php?format=json&method=$command"

        val req = StringRequest (
            Request.Method.GET, url,
            { response ->
                try {
                    cb(JSONObject(response))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }
        )

        requestQueue.add(req)
    }

    fun volleyPost(params: Map<String, String>, cb : (JSONObject) -> Unit) {
        val url = "https://www.curlyspiker.fr/photo/piwigo/ws.php?format=json"

        val req = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                cb(JSONObject(response))
            },
            Response.ErrorListener { error ->
                Log.d("JP", "Error in POST request")
                error.printStackTrace()
            }
        ) {
            override fun getParams(): Map<String, String> {
                return params
            }
        }

        requestQueue.add(req)
    }

    fun volleyImage(url: String, maxW: Int, maxH: Int, cb: (Bitmap) -> Unit) {
        val req = ImageRequest (
            url,
            { bmp ->
                try {
                    cb(bmp)
                } catch (e: Exception) {
                    Log.d("JP", "Problem with picture URL: $url")
                    e.printStackTrace()
                }
            },
            maxW, maxH, ImageView.ScaleType.CENTER_INSIDE, Bitmap.Config.ARGB_8888,
            { error ->
                Log.d("JP", "Problem with picture URL: $url")
                error.printStackTrace()
            }
        )

        requestQueue.add(req)
    }

    fun cancelAllOngoing() {
        requestQueue.cancelAll { true }
    }

}