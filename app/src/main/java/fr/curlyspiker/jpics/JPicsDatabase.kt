package fr.curlyspiker.jpics

import android.content.Context
import androidx.room.*
import java.util.*


@Dao
interface CategoryDao {
    @Query("SELECT * FROM category")
    fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE catId IN (:catIds)")
    fun loadAllByIds(catIds: IntArray): List<Category>

    @Query("SELECT * FROM category WHERE catId=:catId")
    fun loadOneById(catId: Int): Category?

    @Query("SELECT * FROM category WHERE name LIKE :name LIMIT 1")
    fun findByName(name: String): Category?

    @Query("SELECT catId FROM category WHERE parent_id=:catId")
    fun findCategoryChildren(catId: Int): List<Int>

    @Insert
    fun insertAll(vararg category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg category: Category)

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

    @Query("SELECT picId FROM picture")
    fun getAllIds(): List<Int>

    @Query("SELECT picId FROM picture WHERE isArchived=1")
    fun getArchivedIds(): List<Int>

    @Query("SELECT * FROM picture WHERE picId=:picId")
    fun loadOneById(picId: Int): Picture?

    @Insert
    fun insertAll(vararg picture: Picture)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg picture: Picture)

    @Update
    fun update(picture: Picture?)

    @Delete
    fun delete(picture: Picture)

    @Query("DELETE FROM picture WHERE picId=:id")
    fun deleteFromId(id: Int)
}

@Dao
interface PictureCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg picCat: PictureCategoryCrossRef)

    @Query("DELETE FROM picture_category_cross_ref WHERE picId NOT IN (:picIds) AND catId IN (:catIds)")
    fun deletePicsNotInListFromCats(picIds: IntArray, catIds: IntArray)

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

    @Query("SELECT picture.picId FROM picture INNER JOIN picture_category_cross_ref ON picture.picId=picture_category_cross_ref.picId " +
            "INNER JOIN category ON category.catId=picture_category_cross_ref.catId WHERE picture_category_cross_ref.catId=:catId AND picture.isArchived=0")
    fun getPicturesIds(catId: Int): List<Int>
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(vararg picCat: PictureTagCrossRef)

    @Query("DELETE FROM picture_tag_cross_ref WHERE picId NOT IN (:picIds) AND tagId IN (:tagIds)")
    fun deletePicsNotInCats(picIds: IntArray, tagIds: IntArray)

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

@Database(entities = [Category::class, Picture::class, PictureCategoryCrossRef::class, PicTag::class, PictureTagCrossRef::class, User::class], version = 5)
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