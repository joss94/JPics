package fr.curlyspiker.jpics

import android.content.Context
import androidx.room.*


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
}

@Dao
interface PictureDao {

    @Query("SELECT picId FROM picture")
    fun getAllIds(): List<Int>

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
    fun deletePicsNotInCats(picIds: IntArray, catIds: IntArray)

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
    fun getPicturesIds(catId: Int): List<Int>
}

@Database(entities = [Category::class, Picture::class, PictureCategoryCrossRef::class], version = 2)
abstract class JPicsDatabase : RoomDatabase() {
    abstract fun CategoryDao(): CategoryDao
    abstract fun PictureDao(): PictureDao
    abstract fun PictureCategoryDao(): PictureCategoryDao
}

object DatabaseProvider {
    lateinit var db: JPicsDatabase

    fun initDB(ctx: Context) {
        if (!this::db.isInitialized) {
            db = Room.databaseBuilder(
                ctx,
                JPicsDatabase::class.java, "database-name"
            ).allowMainThreadQueries().build()
        }
    }
}