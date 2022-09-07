package fr.curlyspiker.jpics

import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object PiwigoAPI {
    class ImageUploadData (
        val bmp: Bitmap,
        val filename: String,
        val date: Date,
        val author: String = "unknown",
        val creationDate: Date = Date(),
        val comment: String = "") {

    }

    fun pwgImagesGetInfo(id: Int, cb : (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.images.getInfo")
        req.put("image_id", id)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgCategoriesGetImages(cats: List<Int>?, page: Int, perPage: Int, order: String, cb : (Boolean, List<Picture>) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.getImages")

        cats?.let {
            req.put("cat_id", JSONArray(cats))
        }
        req.put("page", page)
        req.put("per_page", perPage)
        req.put("order", order)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val jsonImages = rsp.optJSONArray("images")
            val images = mutableListOf<Picture>()
            if (jsonImages != null) {
                for (i in 0 until jsonImages.length()) {
                    images.add(Picture.fromJson(jsonImages.getJSONObject(i)))
                }
            }
            cb(true, images)
        }
    }

    fun pwgCategoriesGetList(recursive: Boolean, cb : (Boolean, List<Category>) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.getList")
        req.put("recursive", recursive)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val jsonCats = rsp.optJSONArray("categories")

            val cats = mutableListOf<Category>()
            if (jsonCats != null) {
                for (i in 0 until jsonCats.length()) {
                    cats.add(Category.fromJson(jsonCats.getJSONObject(i)))
                }
            }

            cb(true, cats)
        }
    }

    fun pwgCategoriesAdd(name: String, parentId: Int?, isPublic: Boolean, visible: Boolean, cb: (Boolean, Int, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.add")
        req.put("name", name)
        req.put("parent", parentId)
        req.put("visible", visible)
        req.put("status", if (isPublic) "public" else "private")

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val id = rsp.optInt("id")
            cb(true, id, rsp) //TODO: Build correct reply
        }
    }

    fun pwgCategoriesDelete(categoryId: Int, pwgToken: String, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.delete")
        req.put("category_id", categoryId)
        req.put("pwg_token", pwgToken)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgCategoriesDeleteRepresentative(categoryId: Int, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.deleteRepresentative")
        req.put("category_id", categoryId)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp) //TODO: Build correct reply
        }
    }

    fun pwgCategoriesMove(catId: Int, pwgToken: String, parent: Int, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.move")
        req.put("category_id", catId)
        req.put("parent", parent)
        req.put("pwg_token", pwgToken)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgCategoriesSetInfo(catId: Int, name: String, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.setInfo")
        req.put("category_id", catId)
        req.put("name", name)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgCategoriesSetRepresentative(categoryId: Int, imageId: Int, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.categories.setRepresentative")
        req.put("category_id", categoryId)
        req.put("image_id", imageId)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp) //TODO: Build correct reply
        }
    }

    fun pwgImagesAddChunk(data: String, originalSum: String, position: String, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.images.addChunk")
        req.put("data", data)
        req.put("original_sum", originalSum)
        req.put("position", position)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgImagesCheckUpload(cb: (Boolean, Boolean) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.images.checkUpload")

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp.optBoolean("ready_for_upload", false))
        }
    }

    fun pwgImagesExist(bytes: List<ByteArray>, cb: (Boolean, List<Int?>) -> Unit) {
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

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val ids = mutableListOf<Int?>()
            for (md5 in md5sumList) {
                val id = rsp.optInt(md5)
                ids.add(if (id > 0) id else null)
            }
            cb(true, ids)
        }
    }

    fun pwgImagesAdd(originalSum: String, filename: String, name: String, author: String, dateCreation: Date, comment: String, cats: List<Int>?, tags: List<Int>?, cb: (Boolean, Int, JSONObject) -> Unit) {
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

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp.optInt("image_id", 0), rsp) //TODO: Build correct reply
        }
    }

    fun pwgImagesDelete(ids: List<Int>, pwgToken: String, cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.images.delete")
        req.put("image_id", JSONArray(ids))
        req.put("pwg_token", pwgToken)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgImagesSetInfo(
        id: Int,
        name: String? = null,
        creationDate: Date? = null,
        categories: List<Int>? = null,
        tags: List<Int>? = null,
        singleValueMode: String = "fill_if_empty",
        multipleValueMode: String = "append",
        cb: (Boolean, JSONObject) -> Unit)
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

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp) //TODO: Build correct reply
        }
    }

    fun pwgSessionGetStatus(cb: (String, Boolean, String, List<String>) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.session.getStatus")

        PiwigoServerHelper.volleyPost(req) { rsp ->
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

            cb(username, isAdmin, token, sizes)
        }
    }

    fun pwgSessionLogin(username: String, password: String, cb: (Boolean, Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.session.login")
        req.put("username", username)
        req.put("password", password)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val success = rsp.optString("stat", "error") == "ok"
            var logged = false
            if (success) {
                logged = rsp.optBoolean("result")
            }
            cb(success, logged, rsp)
        }
    }

    fun pwgSessionLogout(cb: (Boolean, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.session.logout")

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp)
        }
    }

    fun pwgTagsAdd(tag: PicTag, cb: (Boolean, Int?, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.tags.add")
        req.put("name", tag.name)

        PiwigoServerHelper.volleyPost(req) { rsp ->
            cb(true, rsp.optInt("id"), rsp)
        }
    }

    fun pwgTagsGetAdminList(cb: (Boolean, List<PicTag>) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.tags.getAdminList")

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val tagJson = rsp.optJSONArray("tags")
            val tags = mutableListOf<PicTag>()

            if (tagJson != null) {
                for (i in 0 until tagJson.length()) {
                    val tag = tagJson.getJSONObject(i)
                    tags.add(PicTag(tag.getString("id").toInt(), tag.getString("name")))
                }
            }
            cb(true, tags)
        }
    }

    fun pwgUsersGetList(cb: (Boolean, List<User>, JSONObject) -> Unit) {
        val req = JSONObject()

        req.put("method", "pwg.users.getList")

        PiwigoServerHelper.volleyPost(req) { rsp ->
            val usersJson = rsp.optJSONArray("users")
            val users = mutableListOf<User>()
            if (usersJson != null) {
                for (i in 0 until usersJson.length()) {
                    val user = usersJson.getJSONObject(i)
                    users.add(User(user.getInt("id"), user.getString("username")))
                }
            }
            cb(true, users, rsp)
        }
    }

    private fun dateToSqlString(date: Date) : String {
        val dateFormatter = SimpleDateFormat("YYYY-MM-dd", Locale.getDefault())

        //return the formatted date string
        return dateFormatter.format(date)
    }
}