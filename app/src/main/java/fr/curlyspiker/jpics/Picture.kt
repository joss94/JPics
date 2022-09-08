package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
@Entity
data class Picture (
    @PrimaryKey val picId: Int,
    var name: String) {
    @ColumnInfo(name="thumbnail_url") var thumbnailUrl: String = ""
    var largeResUrl: String = ""
    var elementUrl: String = ""

    var creationDate: Date = Date()
        set(value) {
            field = value
            val cal = Calendar.getInstance()
            cal.time = field
            cal.set(Calendar.HOUR, 0)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            creationDay = cal.time
        }

    var creationDay: Date = Date()

    var isArchived = false

    @Ignore
    var mInfo: JSONObject? = null

    init {
        val cal = Calendar.getInstance()
        creationDate = cal.time
    }

    fun isVideo() : Boolean {
        return elementUrl.endsWith(".mp4")
    }

    fun saveToLocal(context: Context, path: String, callback: () -> Unit = {}) {
        val target = object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                try {
                    Toast.makeText(context, "Downloaded of $name finished !", Toast.LENGTH_SHORT).show()
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
        Picasso.with(context).load(elementUrl).into(target)
    }

    fun getTags() : List<Int> {
        return DatabaseProvider.db.PictureTagDao().getTagsFromPicture(picId)
    }

    fun getCategories(recursive: Boolean = false) : List<Int> {

        val out = mutableListOf<Int>()
        val directParents = DatabaseProvider.db.PictureCategoryDao().getParentsIds(picId)
        for (cat in directParents) {
            out.add(cat)

            if(recursive) {
                val parents = PiwigoData.getCategoryFromId(cat)?.getHierarchy()
                parents?.forEach { parent ->
                    if (!out.contains(parent)) {
                        out.add(parent)
                    }
                }
            }
        }
        return out
    }

    fun getCategoriesFromInfoJson() : List<Int> {
        val out = mutableListOf<Int>()
        val catsArray = mInfo?.optJSONArray("categories") ?: JSONArray()
        for(i in 0 until catsArray.length()) {
            out.add(catsArray.getJSONObject(i).optInt("id"))
        }
        return out
    }

    fun getInfo(forceRefresh: Boolean = false, cb: (info: JSONObject) -> Unit = {}) {
        if(mInfo == null || forceRefresh) {
            PiwigoAPI.pwgImagesGetInfo(picId) { success, rsp ->
                if(success) {
                    mInfo = rsp
                }
                cb(mInfo ?: JSONObject())
            }
        }
        else cb(mInfo ?: JSONObject())
    }

    fun getRepresentedBy() : List<Int> {
        val out = mutableListOf<Int>()
        Log.d("TAG", "Pic thumbnail url: $thumbnailUrl")
        PiwigoData.getAllCategories().forEach{
            if(it.thumbnailUrl == thumbnailUrl) {
                out.add(it.catId)
                Log.d("TAG", "This pic was thumbnail to ${it.catId}")
            }
        }
        return out
    }

    companion object {
        fun fromJson(json: JSONObject) : Picture {
            val id = json.optInt("id")
            var name = json.optString("name", "null")
            if(name == "null") {
                name = json.optString("file", "unknown")
            }

            val p = Picture(id, name)
            val derivatives = json.optJSONObject("derivatives")
            p.thumbnailUrl = derivatives?.optJSONObject("thumb")?.optString("url")?:""
            p.largeResUrl = derivatives?.optJSONObject("xxlarge")?.optString("url")?:""
            p.elementUrl = json.optString("element_url")
            p.isArchived = json.optBoolean("is_archived", false)

            val creationString = json.optString("date_creation", "")
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            try {
                p.creationDate = format.parse(creationString) ?: Date()
            } catch (e: java.lang.Exception) {}

            p.mInfo = json

            return p
        }
    }
}

@Entity(primaryKeys = ["picId", "catId"], tableName = "picture_category_cross_ref")
data class PictureCategoryCrossRef (val picId: Int,
                                    val catId: Int)


@Entity(primaryKeys = ["picId", "tagId"], tableName = "picture_tag_cross_ref")
data class PictureTagCrossRef (val picId: Int,
                                    val tagId: Int)