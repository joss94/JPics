package fr.curlyspiker.jpics

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Entity
data class Category (
    @PrimaryKey val catId: Int,
    var name: String,
    @ColumnInfo(name = "parent_id") var parentId: Int = -1
) {

    var thumbnailId: Int = -1
    var thumbnailUrl: String = ""

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

    fun getPicturesIds(recursive: Boolean = false) : Flow<List<Int>>  {

        val cats = mutableListOf(catId)
        if(recursive) {
            cats.addAll(getChildren())
        }
        return DatabaseProvider.db.PictureCategoryDao().getPicturesIds(cats)
    }

    fun getPictures(recursive: Boolean = false) : Flow<List<Picture>> {
        val cats = mutableListOf(catId)
        if(recursive) {
            cats.addAll(getChildren())
        }
        return DatabaseProvider.db.PictureCategoryDao().getPictures(cats)
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

@Entity
data class CategoryWithChildren (
    @Embedded val category: Category,
    @Relation(
        parentColumn = "catId",
        entityColumn = "parent_id"
    ) val children: List<Category>,
    @Relation(
        parentColumn = "parent_id",
        entityColumn = "catId"
    ) val parent: Category?
    )