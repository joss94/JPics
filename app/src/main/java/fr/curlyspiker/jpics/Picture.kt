package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import com.squareup.picasso.Picasso
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
class Picture (val id: Int, val name: String) {

    var thumbnailUrl: String = ""
    var fullResUrl: String = ""

    var creationDate: Date
    var creationDay: Date

    var infos: JSONObject = JSONObject()

    init {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.set(Calendar.HOUR, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        creationDate = cal.time
        creationDay = cal.time
    }

    fun saveToLocal(context: Context, path: String) {
        Picasso.with(context).load(fullResUrl).into(object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                try {
                    val folder = File(path)
                    if(!folder.exists()) { folder.mkdirs() }
                    val file = File(folder.path + File.separator + name + ".jpg")
                    file.createNewFile()
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    stream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onBitmapFailed(errorDrawable: Drawable?) { Log.d("TAG", "Error during download !") }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        })
    }

    fun getCategories(recursive : Boolean = false) : List<Category>{
        val out = CategoriesManager.categories.filter { c -> c.picturesIDs.contains(id) }.toMutableList()

        if(recursive) {
            out.forEach { c ->
                val parents = c.getHierarchy()
                parents.forEach { parent ->
                    if(!out.contains(parent)) {
                        out.add(parent)
                    }
                }
            }
        }
        return out
    }

    companion object {
        fun fromJson(json: JSONObject) : Picture {
            val id = json.optInt("id")
            val name = json.optString("name")

            val p = Picture(id, name)
            val derivatives = json.optJSONObject("derivatives")
            p.thumbnailUrl = derivatives?.optJSONObject("thumb")?.optString("url")?:""
            p.fullResUrl = derivatives?.optJSONObject("xxlarge")?.optString("url")?:""

            val creationString = json.optString("date_creation", "")
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            try {
                p.creationDate = format.parse(creationString) ?: Date()
            } catch (e: java.lang.Exception) {}

            val cal = Calendar.getInstance()
            cal.time = p.creationDate
            cal.set(Calendar.HOUR, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            p.creationDay = cal.time

            p.infos = json

            return p
        }
    }
}