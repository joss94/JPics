package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.room.*
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

    fun getTags() : List<Int> {
        return DatabaseProvider.db.PictureTagDao().getTagsFromPicture(picId)
    }

    fun getCategoriesFromInfoJson() : List<Int> {
        val out = mutableListOf<Int>()
        val catsArray = mInfo?.optJSONArray("categories") ?: JSONArray()
        for(i in 0 until catsArray.length()) {
            out.add(catsArray.getJSONObject(i).optInt("id"))
        }
        return out
    }

    fun getTagsFromInfoJson() : List<Int> {
        val out = mutableListOf<Int>()
        val tagsArray = mInfo?.optJSONArray("tags") ?: JSONArray()
        for(i in 0 until tagsArray.length()) {
            out.add(tagsArray.getJSONObject(i).optInt("id"))
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
            p.isArchived = json.optString("is_archived", "") == "1"

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

@Entity(
    primaryKeys = ["picId", "catId"],
    tableName = "picture_category_cross_ref" ,
    foreignKeys = [
        ForeignKey(
            entity = Picture::class,
            parentColumns = ["picId"],
            childColumns = ["picId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["catId"],
            childColumns = ["catId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE)
    ]
)
data class PictureCategoryCrossRef (val picId: Int,
                                    val catId: Int)


@Entity(primaryKeys = ["picId", "tagId"], tableName = "picture_tag_cross_ref")
data class PictureTagCrossRef (val picId: Int,
                                    val tagId: Int)