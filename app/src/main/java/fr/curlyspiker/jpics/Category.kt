package fr.curlyspiker.jpics

import android.util.Log
import org.json.JSONObject

object CategoriesManager {

    var categories: MutableList<Category> = mutableListOf()

    init{
        addCategory(Category(0, "Home"))
    }

    fun addCategory(cat: Category) {
        if (categories.indexOfFirst { c -> c.id == cat.id } == -1) {
            categories.add(cat)
        }
    }

    fun fromID(id: Int): Category? {
        return categories.firstOrNull { c->  c.id == id }
    }
}

class Category (
    val id: Int,
    val name: String,
    val parentId: Int = -1,
    val nPictures: Int = 0
    ) {

    var thumbnailId: Int = -1
    var thumbnailUrl: String = ""

    var pictures : MutableList<Picture> = mutableListOf()

    companion object {
        fun fromJson(json: JSONObject) : Category {
            val id = json.optInt("id")
            val name = json.optString("name")

            val rankString = json.optString("uppercats")
            val ranks = rankString.split(",")
            var parentId = 0
            if (ranks.size >= 2) {
                parentId = ranks[ranks.size - 2].toInt()
            }

            val nPictures = json.optInt("nb_images", 0)

            val c = Category(id, name, parentId, nPictures)
            c.thumbnailId = json.optString("representative_picture_id").toIntOrNull()?:-1
            c.thumbnailUrl = json.optString("tn_url")

            return c
        }
    }

    init {
        val params = "&cat_id=${id}&per_page=${nPictures}"
        Log.d("TAG", "params: $params")
        PiwigoServerHelper.volleyGet("pwg.categories.getImages$params") { rsp ->
            if(rsp.optString("stat") == "ok") {
                val result = rsp.optJSONObject("result")
                val images = result?.optJSONArray("images")

                Log.d("TAG", "Received category $name pictures: ${images?.length()} pics")

                images?.let {
                    for (i in 0 until it.length()) {
                        pictures.add(Picture.fromJson(it.optJSONObject(i)))
                    }
                }
            }
        }
    }

    fun getChildren(): List<Category> {
        return CategoriesManager.categories.filter { c -> c.parentId == id}
    }

    fun getParent() : Category? {
        return CategoriesManager.categories.firstOrNull { c -> c.id == parentId }
    }

    fun getHierarchy() : List<Category> {
        val parents: MutableList<Category> = mutableListOf()
        val parent = getParent()
        parent?.let{
            parents.addAll(parent.getHierarchy())
        }
        parents.add(this)
        return parents
    }
}

