package fr.curlyspiker.jpics

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.squareup.picasso.Picasso
import java.lang.Float.max
import java.lang.Float.min


class ImageViewerActivity : Activity() {

    private lateinit var pager : ViewPager
    private lateinit var pagerAdapter: ImageViewerPager

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        pager = findViewById(R.id.pager)

        val catId = savedInstanceState?.getInt("cat_id") ?: intent.getIntExtra("cat_id", 0)
        val index = savedInstanceState?.getInt("img_index") ?: intent.getIntExtra("img_index", 0)

        pagerAdapter = ImageViewerPager(applicationContext, CategoriesManager.fromID(catId)!!)
        pager.adapter = pagerAdapter
        pager.currentItem = index

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
    }

    override fun onSaveInstanceState (outState: Bundle) {
        outState.putInt("cat_id", pagerAdapter.category.id)
        outState.putInt("img_index", pager.currentItem)
    }

    /*
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        scaleGestureDetector.onTouchEvent(ev)
        return true
    }
    */

    class ImageViewerPager (private val mContext: Context, cat: Category) : PagerAdapter() {

        var category: Category = cat
        var imageView: ImageView? = null

        override fun getCount(): Int {
            return category.nPictures
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj as ConstraintLayout
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {

            Log.d("TAG", "Loading image $position")
            // Declare Variables
            val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val itemView: View = inflater.inflate(R.layout.image_viewer_page, container, false)

            imageView = itemView.findViewById(R.id.image_view)

            if(position < category.pictures.size) {
                val picture = category.pictures[position]
                val imgUrl = picture.fullResUrl
                if(imgUrl.isNotEmpty()) {
                    Picasso.with(mContext).load(imgUrl).into(imageView)
                }

                // Add viewpager_item.xml to ViewPager
                (container as ViewPager).addView(itemView)
            }

            return itemView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            // Remove viewpager_item.xml from ViewPager
            (container as ViewPager).removeView(`object` as ConstraintLayout?)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            scaleFactor *= scaleGestureDetector.scaleFactor
            scaleFactor = max(0.1f, min(scaleFactor, 10.0f))
            pagerAdapter.imageView?.scaleX = scaleFactor
            pagerAdapter.imageView?.scaleY = scaleFactor
            pager.invalidate()
            return true
        }
    }
}