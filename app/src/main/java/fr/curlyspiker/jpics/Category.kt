package fr.curlyspiker.jpics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity
data class Category (
    @PrimaryKey val catId: Int,
    var name: String,
    @ColumnInfo(name = "parent_id") var parentId: Int = -1
) {

    var thumbnailId: Int = -1
    var thumbnailUrl: String = ""
        get() {
            if (field.isEmpty() && getPictures(recursive = true).isNotEmpty()) {
                PiwigoData.refreshCategoryRepresentative(catId)
            }
            return field
        }

    companion object {
        fun fromJson(json: JSONObject) : Category {
            val id = json.optInt("id")
            val name = json.optString("name")

            val rankString = json.optString("uppercats")
            val ranks = rankString.split(",")
            var parentId = 0
            if(name.contains("JPicsArchiveFolder") || name.contains("JPicsInstantUpload") || name.contains("JPicsNoAlbum")) {
                parentId = -1
            }
            else if (ranks.size >= 2) {
                parentId = ranks[ranks.size - 2].toInt()
            }

            val c = Category(id, name, parentId)
            c.thumbnailId = json.optString("representative_picture_id").toIntOrNull()?:-1
            c.thumbnailUrl = json.optString("tn_url")

            return c
        }
    }

    fun getChildren(): List<Int> {
        return DatabaseProvider.db.CategoryDao().findCategoryChildren(catId)
    }

    fun getPictures(recursive: Boolean = false) : List<Int>  {

        val out = mutableListOf<Int>()
        out.addAll(DatabaseProvider.db.PictureCategoryDao().getPicturesIds(catId))

        if(recursive) {
            getChildren().forEach { out.addAll(PiwigoData.getCategoryFromId(it)?.getPictures(recursive) ?: listOf()) }
        }
        return out
    }

    fun getHierarchy() : List<Int> {
        val parents: MutableList<Int> = mutableListOf()

        parents.add(catId)
        PiwigoData.getCategoryFromId(parentId)?.getHierarchy()?.forEach { p ->
            if(p !in parents) {
                parents.add(p)
            }
        }

        return parents
    }
}