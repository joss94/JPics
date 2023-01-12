package fr.curlyspiker.jpics

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object PiwigoAPI {

    private suspend fun postRequest(req: JSONObject): JSONObject{
        var ret = JSONObject()
        try {
            ret = PiwigoServerHelper.volleyPost(req)
        } catch (e: Exception) {
            LogManager.addLog("An API error occurred: Request: ${req.toString().take(500)}, Error: $e")
        }
        return ret
    }

    suspend fun pwgImagesGetInfo(id: Int) : JSONObject {
        val req = JSONObject()

        req.put("method", "pwg.images.getInfo")
        req.put("image_id", id)

        return postRequest(req)
    }

    suspend fun jpicsCategoriesGetImages(cats: List<Int>?, page: Int, perPage: Int, order: String): List<Picture> {
        val req = JSONObject()

        req.put("method", "jpics.categories.getImages")

        cats?.let {
            req.put("cat_id", JSONArray(cats))
        }
        req.put("page", page)
        req.put("per_page", perPage)
        req.put("order", order)

        val rsp = postRequest(req)
        val jsonImages = rsp.optJSONArray("images")
        val images = mutableListOf<Picture>()
        if (jsonImages != null) {
            for (i in 0 until jsonImages.length()) {
                images.add(Picture.fromJson(jsonImages.getJSONObject(i)))
            }
        }
        return images
    }

    suspend fun pwgCategoriesGetList(recursive: Boolean): List<Category> {
        val req = JSONObject()

        req.put("method", "pwg.categories.getList")
        req.put("recursive", recursive)

        val rsp = postRequest(req)
        val jsonCats = rsp.optJSONArray("categories")

        val cats = mutableListOf<Category>()
        if (jsonCats != null) {
            for (i in 0 until jsonCats.length()) {
                cats.add(Category.fromJson(jsonCats.getJSONObject(i)))
            }
        }
        return cats
    }

    suspend fun pwgCategoriesAdd(
        name: String,
        parentId: Int?,
        isPublic: Boolean,
        visible: Boolean
    ): Int {
        val req = JSONObject()

        req.put("method", "pwg.categories.add")
        req.put("name", name)
        req.put("parent", parentId)
        req.put("visible", visible)
        req.put("status", if (isPublic) "public" else "private")

        val rsp = postRequest(req)
        return rsp.optInt("id", 0)
    }

    suspend fun pwgCategoriesDelete(categoryId: Int, pwgToken: String) {
        val req = JSONObject()

        req.put("method", "pwg.categories.delete")
        req.put("category_id", categoryId)
        req.put("pwg_token", pwgToken)
        postRequest(req)
    }

    suspend fun pwgCategoriesDeleteRepresentative(categoryId: Int) {
        val req = JSONObject()

        req.put("method", "pwg.categories.deleteRepresentative")
        req.put("category_id", categoryId)
        postRequest(req)
    }

    suspend fun pwgCategoriesMove(catId: Int, pwgToken: String, parent: Int) {
        val req = JSONObject()

        req.put("method", "pwg.categories.move")
        req.put("category_id", catId)
        req.put("parent", parent)
        req.put("pwg_token", pwgToken)
        postRequest(req)
    }

    suspend fun pwgCategoriesSetInfo(catId: Int, name: String) {
        val req = JSONObject()

        req.put("method", "pwg.categories.setInfo")
        req.put("category_id", catId)
        req.put("name", name)

        postRequest(req)
    }

    suspend fun pwgCategoriesSetRepresentative(categoryId: Int, imageId: Int) {
        val req = JSONObject()

        req.put("method", "pwg.categories.setRepresentative")
        req.put("category_id", categoryId)
        req.put("image_id", imageId)

        postRequest(req)
    }

    suspend fun pwgImagesAddChunk(data: String, originalSum: String, position: String) {
        val req = JSONObject()

        req.put("method", "pwg.images.addChunk")
        req.put("data", data)
        req.put("original_sum", originalSum)
        req.put("position", position)

        postRequest(req)
    }

    suspend fun pwgImagesCheckUpload(): Boolean {
        val req = JSONObject()
        req.put("method", "pwg.images.checkUpload")
        val rsp = postRequest(req)
        return rsp.optBoolean("ready_for_upload", false)
    }

    suspend fun pwgImagesExist(bytes: List<ByteArray>): List<Int?> {
        val req = JSONObject()

        req.put("method", "pwg.images.exist")

        val md5sumList = mutableListOf<String>()
        var md5sumListReq = ""
        bytes.forEach { b ->
            val md5 = Utils.md5(b)
            md5sumList.add(Utils.md5(b))
            md5sumListReq += md5
            md5sumListReq += ","
        }
        req.put("md5sum_list", md5sumListReq)

        val ids = mutableListOf<Int?>()

        val rsp = postRequest(req)
        for (md5 in md5sumList) {
            val id = rsp.optInt(md5, -1)
            ids.add(if (id > 0) id else null)
        }

        return ids
    }

    suspend fun pwgImagesAdd(
        originalSum: String,
        filename: String,
        name: String,
        author: String,
        dateCreation: Date,
        comment: String,
        cats: List<Int>?,
        tags: List<Int>?
    ): Int {
        val req = JSONObject()

        req.put("method", "pwg.images.add")
        req.put("original_sum", originalSum)
        req.put("original_filename", filename)
        req.put("name", name)
        req.put("author", author)

        req.put("date_creation", dateToSqlString(dateCreation))
        req.put("comment", comment)

        var catsList = ""
        cats?.forEach { c ->
            catsList += c.toString()
            catsList += ","
        }
        req.put("categories", catsList)

        var tagsList = ""
        tags?.forEach { t ->
            tagsList += t.toString()
            tagsList += ","
        }
        req.put("tag_ids", tagsList)

        val rsp = postRequest(req)
        return rsp.optInt("image_id", 0)
    }

    suspend fun pwgImagesDelete(ids: List<Int>, pwgToken: String) {
        val req = JSONObject()

        req.put("method", "pwg.images.delete")
        req.put("image_id", JSONArray(ids))
        req.put("pwg_token", pwgToken)
        postRequest(req)
    }

    suspend fun pwgImagesSetInfo(
        id: Int,
        name: String? = null,
        creationDate: Date? = null,
        categories: List<Int>? = null,
        tags: List<Int>? = null,
        singleValueMode: String = "fill_if_empty",
        multipleValueMode: String = "append")
    {
        val req = JSONObject()

        req.put("method", "pwg.images.setInfo")
        req.put("image_id", id)
        req.put("name", name)
        creationDate?.let { req.put("date_creation", dateToSqlString(creationDate)) }
        req.put("categories", JSONArray(categories))
        req.put("tag_ids", JSONArray(tags))
        req.put("single_value_mode", singleValueMode)
        req.put("multiple_value_mode", multipleValueMode)

        postRequest(req)
    }

    suspend fun pwgJpicsImagesArchive(imageIds: List<Int>, archive: Boolean) {
        val req = JSONObject()

        req.put("method", "jpics.images.archive")
        req.put("image_id", JSONArray(imageIds))
        req.put("archive", archive)

        postRequest(req)
    }

    suspend fun pwgJpicsImagesMoveToCategory(imageIds: List<Int>, categoryId: Int) {
        val req = JSONObject()

        req.put("method", "jpics.images.moveToCategory")
        req.put("image_id", JSONArray(imageIds))
        req.put("cat_id", categoryId)

        postRequest(req)
    }

    suspend fun pwgSessionGetStatus(): JSONObject {
        val req = JSONObject()

        req.put("method", "pwg.session.getStatus")

        /*
        val username = rsp.optString("username", "unknown")
            val isAdmin = rsp.optString("status", "guest") == "admin"
            val token = rsp.optString("pwg_token")
            val sizes = mutableListOf<String>()
            val sizesArray = rsp.optJSONArray("available_sizes")
            if (sizesArray != null) {
                for (i in 0 until sizesArray.length()) {
                    sizes.add(sizesArray[i].toString())
                }
            }
         */
        return postRequest(req)
    }

    suspend fun pwgSessionLogin(username: String, password: String,): Boolean {
        val req = JSONObject()

        req.put("method", "pwg.session.login")
        req.put("username", username)
        req.put("password", password)


        val rsp = postRequest(req)
        val success = rsp.optString("stat", "error") == "ok"

        return success && rsp.optBoolean("result", false)
    }

    suspend fun pwgSessionLogout() {
        val req = JSONObject()

        req.put("method", "pwg.session.logout")

        postRequest(req)
    }

    suspend fun pwgTagsAdd(tag: PicTag): Int {
        val req = JSONObject()

        req.put("method", "pwg.tags.add")
        req.put("name", tag.name)

        val rsp = postRequest(req)
        return rsp.optInt("id", -1)
    }

    suspend fun pwgTagsGetAdminList(): List<PicTag> {
        val req = JSONObject()

        req.put("method", "pwg.tags.getAdminList")

        val tags = mutableListOf<PicTag>()
        val rsp = postRequest(req)
        val tagJson = rsp.optJSONArray("tags")

        if (tagJson != null) {
            for (i in 0 until tagJson.length()) {
                val tag = tagJson.getJSONObject(i)
                tags.add(PicTag(tag.getString("id").toInt(), tag.getString("name")))
            }
        }

        return tags
    }

    suspend fun pwgTagsGetImages(tags: List<Int>? = null, tagNames: List<String>? = null, perPage: Int = 500, page: Int = 0 ,order: String = "id"): List<Int> {
        val req = JSONObject()

        req.put("method", "pwg.tags.getImages")

        tags?.let {
            req.put("tag_id", JSONArray(tags))
        }
        tagNames?.let {
            req.put("tag_name", JSONArray(tagNames))
        }
        req.put("page", page)
        req.put("per_page", perPage)
        req.put("order", order)

        val rsp = postRequest(req)
        val jsonImages = rsp.optJSONArray("images")
        val images = mutableListOf<Int>()
        if (jsonImages != null) {
            for (i in 0 until jsonImages.length()) {
                val id = jsonImages.getJSONObject(i).optInt("id", -1)
                if (id > 0) {
                    images.add(id)
                }
            }
        }
        return images
    }

    suspend fun pwgUsersGetList(): List<User> {
        val req = JSONObject()

        req.put("method", "pwg.users.getList")

        val users = mutableListOf<User>()
        val rsp = postRequest(req)
        val usersJson = rsp.optJSONArray("users")

        if (usersJson != null) {
            for (i in 0 until usersJson.length()) {
                val user = usersJson.getJSONObject(i)
                users.add(User(user.getInt("id"), user.getString("username")))
            }
        }

        return users
    }

    private fun dateToSqlString(date: Date) : String {
        val dateFormatter = SimpleDateFormat("YYYY-MM-dd HH:mm:ss", Locale.getDefault())

        //return the formatted date string
        return dateFormatter.format(date)
    }
}