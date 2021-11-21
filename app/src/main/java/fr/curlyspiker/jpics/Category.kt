package fr.curlyspiker.jpics

import org.json.JSONObject

class Category (
    val id: Int,
    var name: String,
    var parentId: Int = -1
) {

    var thumbnailId: Int = -1
    var thumbnailUrl: String = ""
        get() {
            if (field.isEmpty() && getPictures(recursive = true).isNotEmpty()) {
                PiwigoData.refreshCategoryRepresentative(id)
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
        return PiwigoData.categories.filter { e -> e.value.parentId == id}.keys.toList()
    }

    fun getPictures(recursive: Boolean = false, getArchived: Boolean = false) : List<Int>  {

        val out = mutableListOf<Int>()
        PiwigoData.picturesCategories.filter { pair -> pair.second == id && (!(PiwigoData.pictures[pair.first]?.isArchived ?: false) || getArchived) }.forEach {
            out.add(it.first)
        }

        if(recursive) {
            getChildren().forEach { out.addAll(PiwigoData.categories[it]?.getPictures(recursive, getArchived) ?: listOf()) }
        }
        return out
    }

    fun getHierarchy() : List<Int> {
        val parents: MutableList<Int> = mutableListOf()

        parents.add(id)
        PiwigoData.categories[parentId]?.getHierarchy()?.forEach { p ->
            if(p !in parents) {
                parents.add(p)
            }
        }

        return parents
    }
}