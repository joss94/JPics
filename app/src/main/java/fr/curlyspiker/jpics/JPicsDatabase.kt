package fr.curlyspiker.jpics

import android.content.Context
import android.provider.SyncStateContract.Helpers.insert
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*


@Dao
interface CategoryDao {
    @Query("SELECT * FROM category")
    fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE catId IN (:catIds)")
    fun loadAllByIds(catIds: IntArray): List<Category>

    @Query("SELECT * FROM category WHERE catId=:catId")
    fun loadOneById(catId: Int): Category?

    @Query("SELECT * FROM category WHERE catId IN (:catIds)")
    fun loadManyById(catIds: List<Int>): List<Category>

    @Transaction
    @Query("SELECT * FROM category WHERE catId=:catId")
    fun loadOneByIdWithChildrenFlow(catId: Int): Flow<CategoryWithChildren>?

    @Query("SELECT * FROM category WHERE catId=:catId")
    fun loadOneByIdFlow(catId: Int): Flow<Category>?

    @Query("SELECT * FROM category WHERE name LIKE :name LIMIT 1")
    fun findByName(name: String): Category?

    @Query("SELECT catId FROM category WHERE parent_id=:catId")
    fun findCategoryChildren(catId: Int): List<Int>

    @Insert
    fun insertAll(vararg category: Category)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(category: Category): Long

    @Transaction
    fun insertOrReplace(category: Category) {
        val id: Long = insert(category)
        if (id == -1L) {
            update(category)
        }
    }

    @Transaction
    fun insertOrReplace(cats: List<Category>) {
        for (cat in cats) {
            insertOrReplace(cat)
        }
    }

    @Update
    fun update(cat: Category?)

    @Delete
    fun delete(category: Category)

    @Query("DELETE FROM category WHERE catId=:id")
    fun deleteFromId(id: Int)

    @Query("DELETE FROM category WHERE catId NOT IN (:catIds)")
    fun deleteIdsNotInList(catIds: List<Int>)
}

@Dao
interface PictureDao {

    @Query("SELECT picId FROM picture ORDER BY creationDate")
    fun getAllIds(): List<Int>

    @Query("SELECT * FROM picture ORDER BY creationDate")
    fun getAllPictures(): Flow<List<Picture>>

    @Query("SELECT picId FROM picture WHERE isArchived=1 ORDER BY creationDate")
    fun getArchivedIds(): List<Int>

    @Query("SELECT * FROM picture WHERE picId=:picId")
    fun loadOneById(picId: Int): Picture?

    @Query("SELECT * FROM picture WHERE picId IN (:picIds)")
    fun loadManyById(picIds: List<Int>): List<Picture>

    @Insert
    fun insertAll(vararg picture: Picture)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(picture: Picture): Long

    @Transaction
    fun insertOrReplace(picture: Picture) {
        val id: Long = insert(picture)
        if (id == -1L) {
            update(picture)
        }
    }

    @Transaction
    fun insertOrReplace(pictures: List<Picture>) {
        for (picture in pictures) {
            insertOrReplace(picture)
        }
    }

    @Update
    fun update(picture: Picture?)

    @Delete
    fun delete(picture: Picture)

    @Query("DELETE FROM picture WHERE picId=:id")
    fun deleteFromId(id: Int)

    @Query("DELETE FROM picture WHERE picId IN (:ids)")
    fun deleteAll(ids: List<Int>)
}

@Dao
interface PictureCategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(picCat: PictureCategoryCrossRef): Long

    @Update
    fun update(picCat: PictureCategoryCrossRef?)

    @Transaction
    fun insertOrReplace(picCat: PictureCategoryCrossRef) {
        val id: Long = insert(picCat)
        if (id == -1L) {
            update(picCat)
        }
    }

    @Transaction
    fun insertOrReplace(picCats: List<PictureCategoryCrossRef>) {
        for (picCat in picCats) {
            insertOrReplace(picCat)
        }
    }

    @Query("DELETE FROM picture_category_cross_ref WHERE picId NOT IN (:picIds) AND catId IN (:catIds)")
    fun deletePicsNotInListCatInList(picIds: List<Int>, catIds: List<Int>)

    @Query("DELETE FROM picture_category_cross_ref WHERE picId=:picId AND catId NOT IN (:catIds)")
    fun removePicFromOtherCategories(picId: Int, catIds: IntArray)

    @Query("DELETE FROM picture_category_cross_ref WHERE picId NOT IN (:picIds)")
    fun deletePicsNotInList(picIds: IntArray)

    @Query("DELETE FROM picture_category_cross_ref WHERE catId=:catId")
    fun deleteCat(catId: Int)

    @Query("DELETE FROM picture_category_cross_ref WHERE picId=:picId")
    fun deletePic(picId: Int)

    @Query("DELETE FROM picture_category_cross_ref WHERE picId=:picId AND catId!=:catId")
    fun deletePicFromOtherCats(picId: Int, catId: Int)

    @Delete
    fun delete(crossRef: PictureCategoryCrossRef)

    @Query("SELECT catId FROM picture_category_cross_ref WHERE picId=:picId")
    fun getParentsIds(picId: Int): List<Int>

    @Query("SELECT picId FROM picture_category_cross_ref WHERE catId=:catId")
    fun getPicturesIdsWithArchived(catId: Int): List<Int>

    @Query("SELECT * FROM picture INNER JOIN picture_category_cross_ref ON picture.picId=picture_category_cross_ref.picId " +
            "INNER JOIN category ON category.catId=picture_category_cross_ref.catId WHERE picture_category_cross_ref.catId IN (:catIds) AND picture.isArchived=0")
    fun getPictures(catIds: List<Int>): Flow<List<Picture>>

    @Query("SELECT picture.picId FROM picture INNER JOIN picture_category_cross_ref ON picture.picId=picture_category_cross_ref.picId " +
            "INNER JOIN category ON category.catId=picture_category_cross_ref.catId WHERE picture_category_cross_ref.catId IN (:catIds) AND picture.isArchived=0")
    fun getPicturesIds(catIds: List<Int>): Flow<List<Int>>

    @Query("SELECT category.catId FROM category INNER JOIN picture_category_cross_ref ON category.catId=picture_category_cross_ref.catId " +
            "INNER JOIN picture ON picture.picId=picture_category_cross_ref.picId WHERE picture_category_cross_ref.picId=:picId AND picture.thumbnail_url=category.cat_thumbnail_url")
    fun getCategoriesRepresentedByPic(picId: Int): List<Int>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag")
    fun getAll(): List<PicTag>

    @Query("SELECT * FROM tag WHERE tagId IN (:tagIds)")
    fun loadAllByIds(tagIds: IntArray): List<PicTag>

    @Query("SELECT * FROM tag WHERE tagId=:tagId")
    fun loadOneById(tagId: Int): PicTag?

    @Query("SELECT * FROM tag WHERE name LIKE :name LIMIT 1")
    fun findByName(name: String): PicTag?

    @Insert
    fun insertAll(vararg tag: PicTag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg tag: PicTag)

    @Update
    fun update(tag: PicTag?)

    @Delete
    fun delete(tag: PicTag)

    @Query("DELETE FROM tag WHERE tagId=:id")
    fun deleteFromId(id: Int)

    @Query("DELETE FROM tag WHERE tagId NOT IN (:tagIds)")
    fun deleteIdsNotInList(tagIds: List<Int>)
}

@Dao
interface PictureTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(picTag: PictureTagCrossRef): Long

    @Update
    fun update(picTag: PictureTagCrossRef?)

    @Transaction
    fun insertOrReplace(picTag: PictureTagCrossRef) {
        val id: Long = insert(picTag)
        if (id == -1L) {
            update(picTag)
        }
    }

    @Transaction
    fun insertOrReplace(picTags: List<PictureTagCrossRef>) {
        for (picTag in picTags) {
            insertOrReplace(picTag)
        }
    }

    @Query("DELETE FROM picture_tag_cross_ref WHERE tagId=:tagId AND picId NOT IN (:picIds)")
    fun removeTagFromOtherPics(tagId: Int, picIds: List<Int>)

    @Query("DELETE FROM picture_tag_cross_ref WHERE tagId=:tagId")
    fun deleteCat(tagId: Int)

    @Query("DELETE FROM picture_tag_cross_ref WHERE picId=:picId")
    fun deletePic(picId: Int)

    @Query("DELETE FROM picture_tag_cross_ref WHERE picId=:picId AND tagId!=:tagId")
    fun deletePicFromOtherCats(picId: Int, tagId: Int)

    @Delete
    fun delete(crossRef: PictureTagCrossRef)

    @Query("SELECT tagId FROM picture_tag_cross_ref WHERE picId=:picId")
    fun getTagsFromPicture(picId: Int): List<Int>

    @Query("SELECT picId FROM picture_tag_cross_ref WHERE tagId=:tagId")
    fun getPicturesFromTags(tagId: Int): List<Int>
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun getAll(): List<User>

    @Query("SELECT * FROM user WHERE userId=:userId")
    fun loadOneById(userId: Int): User?

    @Query("SELECT * FROM user WHERE username LIKE :name LIMIT 1")
    fun findByName(name: String): User?

    @Insert
    fun insertAll(vararg user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg user: User)

    @Update
    fun update(user: User?)

    @Delete
    fun delete(user: User)

    @Query("DELETE FROM user WHERE userId=:id")
    fun deleteFromId(id: Int)

    @Query("DELETE FROM user WHERE userId NOT IN (:userIds)")
    fun deleteIdsNotInList(userIds: List<Int>)
}

@Database(entities = [Category::class, Picture::class, PictureCategoryCrossRef::class, PicTag::class, PictureTagCrossRef::class, User::class],
    version = 9)
@TypeConverters(Converters::class)
abstract class JPicsDatabase : RoomDatabase() {
    abstract fun CategoryDao(): CategoryDao
    abstract fun TagDao(): TagDao
    abstract fun PictureDao(): PictureDao
    abstract fun PictureCategoryDao(): PictureCategoryDao
    abstract fun PictureTagDao(): PictureTagDao
    abstract fun UserDao(): UserDao
}

object DatabaseProvider {
    lateinit var db: JPicsDatabase

    fun initDB(ctx: Context) {
        if (!this::db.isInitialized) {
            db = Room.databaseBuilder(
                ctx,
                JPicsDatabase::class.java, "JPICS_DATABASE"
            ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}