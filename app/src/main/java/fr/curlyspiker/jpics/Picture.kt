package fr.curlyspiker.jpics

import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject

class Picture (
    val id: Int,
    val w: Int,
    val h: Int,
    val name: String
) {
    var thumbnailUrl: String = ""
    var fullResUrl: String = ""

    companion object {
        fun fromJson(json: JSONObject) : Picture {
            val id = json.optInt("id")
            val w = json.optInt("width")
            val h = json.optInt("height")
            val name = json.optString("name")

            val p = Picture(id, w, h, name)
            val derivatives = json.optJSONObject("derivatives")
            p.thumbnailUrl = derivatives?.optJSONObject("thumb")?.optString("url")?:""
            p.fullResUrl = derivatives?.optJSONObject("xxlarge")?.optString("url")?:""

            return p
        }
    }
}