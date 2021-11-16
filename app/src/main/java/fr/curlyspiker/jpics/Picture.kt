package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
class Picture (val id: Int, var name: String) {

    var thumbnailUrl: String = ""
    var fullResUrl: String = ""

    var creationDate: Date
    var creationDay: Date

    private var mInfo: JSONObject? = null

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

    fun saveToLocal(context: Context, path: String, callback: () -> Unit = {}) {
        Log.d("Picture", "Downloading $name ($fullResUrl)")
        val target = object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                try {
                    Log.d("Picture", "Downloaded $name ($fullResUrl)")
                    val folder = File(path)
                    if(!folder.exists()) { folder.mkdirs() }
                    val file = File(folder.path + File.separator + name + if(name.endsWith(".jpg")) "" else ".jpg")
                    file.createNewFile()
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    stream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    callback()
                }
            }
            override fun onBitmapFailed(errorDrawable: Drawable?) {
                Log.d("TAG", "Error during download !")
                callback()
            }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        }
        Picasso.with(context).load(fullResUrl).into(target)
    }

    fun getCategories(recursive : Boolean = false) : List<Category>{

        fun addToList(out: MutableList<Category>, c: Category, recursive: Boolean) {
            if(!out.contains(c)) {
                out.add(c)

                if(recursive) {
                    val parents = c.getHierarchy()
                    parents.forEach { parent ->
                        addToList(out, parent, recursive)
                    }
                }
            }
        }

        val out = mutableListOf<Category>()
        CategoriesManager.categories.filter { c -> c.picturesIDs.contains(id) }.forEach { c ->addToList(out, c, recursive) }

        return out
    }

    fun getTags() : List<PiwigoSession.PicTag> {
        val out = mutableListOf<PiwigoSession.PicTag>()
        PiwigoSession.getAllTags().forEach { t ->
            if(t.pictures.contains(id)) {
                out.add(t)
            }
        }
        return out
    }

    fun getInfo(forceRefresh: Boolean = false, cb: (info: JSONObject) -> Unit = {}) {
        if(mInfo == null || forceRefresh) {
            PiwigoSession.getPictureInfo(id) { rsp ->
                mInfo = rsp
                cb(rsp)
            }
        }
        else cb(mInfo ?: JSONObject())
    }

    companion object {
        fun fromJson(json: JSONObject) : Picture {
            val id = json.optInt("id")
            var name = json.optString("name", "null")
            if(name == "null") {
                name = json.optString("file", "unkown")
            }

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

            return p
        }
    }
}