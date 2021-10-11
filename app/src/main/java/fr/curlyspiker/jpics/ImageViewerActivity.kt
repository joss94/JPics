package fr.curlyspiker.jpics

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat


class ImageViewerActivity : AppCompatActivity() {

    private lateinit var pager : ViewPager
    private lateinit var pagerAdapter: ImageViewerPager

    private lateinit var infoDialog: BottomSheetDialog

    private lateinit var infoFilename : TextView
    private lateinit var infoCreationDate : TextView
    private lateinit var infoAlbums : TextView
    private lateinit var infoSize : TextView
    private lateinit var infoAddedBy : TextView
    private lateinit var infoTags : TextView

    private var catId: Int = -1


    private var onPermissionsGranted : () -> Unit = {}
    private val permissionReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) { onPermissionsGranted() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        pager = findViewById(R.id.pager)

        catId = savedInstanceState?.getInt("cat_id") ?: intent.getIntExtra("cat_id", -1)
        val index = savedInstanceState?.getInt("img_index") ?: intent.getIntExtra("img_index", 0)

        pagerAdapter = ImageViewerPager(applicationContext)
        pager.adapter = pagerAdapter
        pagerAdapter.pictures = CategoriesManager.currentlyDisplayedList?.toList() ?: listOf()

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageSelected(position: Int) {
                updateBottomSheet(pagerAdapter.pictures[position])
            }
        })

        infoDialog = BottomSheetDialog(this)
        infoDialog.setContentView(R.layout.dialog_image_info)

        infoFilename = infoDialog.findViewById(R.id.image_name)!!
        infoCreationDate = infoDialog.findViewById(R.id.image_creation_date)!!
        infoAlbums = infoDialog.findViewById(R.id.albums)!!
        infoSize = infoDialog.findViewById(R.id.size)!!
        infoAddedBy = infoDialog.findViewById(R.id.info_addedby)!!
        infoTags = infoDialog.findViewById(R.id.info_keywords)!!

        startActionMode(object : ActionMode.Callback{
            override fun onCreateActionMode(mode: ActionMode?,menu: Menu?): Boolean {
                val menuInflater: MenuInflater? = mode?.menuInflater
                menuInflater?.inflate(R.menu.viewer_menu, menu)
                return true
            }

            override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean { return true }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                var out = true
                when (item?.itemId) {
                    R.id.action_download -> downloadImage()
                    R.id.action_move -> moveImage()
                    R.id.action_delete -> deleteImage()
                    R.id.action_info -> showInfo()
                    else -> out = false
                }
                return out
            }

            override fun onDestroyActionMode(p0: ActionMode?) {
                finish()
            }
        })

        pager.currentItem = index
        updateBottomSheet(pagerAdapter.pictures[index])
    }

    private fun updateBottomSheet(pic: Picture) {
        PiwigoSession.getPictureInfo(pic.id) { rsp ->
            pic.infos = rsp

            infoFilename.text = pic.name

            val format = SimpleDateFormat("EEE dd MMMM YYYY HH:MM")
            infoCreationDate.text = format.format(pic.creationDate)

            var albumsTxt = ""
            pic.getCategories().forEach { c -> albumsTxt += "${c.name} - " }
            infoAlbums.text = albumsTxt

            infoSize.text = "${pic.infos.optString("width")} x ${pic.infos.optString("height")}"

            infoAddedBy.text = PiwigoSession.getUser(pic.infos.optString("added_by", "-1").toInt())?.username

            val tags = pic.infos.optJSONArray("tags")
            tags?.let {
                var tagsTxt = ""
                for(i in 0 until tags.length()) {
                    val tagObj = tags.getJSONObject(i)
                    val tagId = tagObj.getString("id").toInt()
                    Log.d("TAG", "Tag id: $tagId    tag name: ${PiwigoSession.getTag(tagId)?.name}")
                    tagsTxt += PiwigoSession.getTag(tagId)?.name + " - "
                }
                infoTags.text = tagsTxt
            }
        }
    }

    private fun downloadImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]
        fun downloadImagesPermissionGranted() {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            picture.saveToLocal(this, path)
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            onPermissionsGranted = { downloadImagesPermissionGranted() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun moveImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]
        selectCategory { c ->
            c?.let {
                PiwigoSession.movePictures(listOf(picture), it) {
                    CategoriesManager.refreshAllPictures {
                        runOnUiThread {
                            pagerAdapter.pictures = CategoriesManager.currentlyDisplayedList?.toList() ?: listOf()
                        }
                    }
                }
            }
        }
    }

    private fun deleteImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Delete image")
            .setMessage("Are you sure you want to delete this image?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { dialog, whichButton ->
                PiwigoSession.deletePictures(listOf(picture))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showInfo() {
        infoDialog.show()
    }

    private fun selectCategory(callback: (cat: Category?) -> Unit) {
        val builder = AlertDialog.Builder(this, R.style.AlertStyle)
        builder.setTitle("Select an album")

        val labels = mutableListOf<String>()
        val catIDs = mutableListOf<Int>()
        CategoriesManager.categories.forEach { c ->
            labels.add(c.name)
            catIDs.add(c.id)
        }

        builder.setSingleChoiceItems(labels.toTypedArray(), -1) { dialog: DialogInterface, i: Int ->
            dialog.dismiss()
            callback(CategoriesManager.fromID(catIDs[i]))
        }

        builder.setNeutralButton("Cancel") { dialog: DialogInterface, i: Int ->
            dialog.cancel()
            callback(null)
        }

        builder.create().show()
    }

    override fun onSaveInstanceState (outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("img_index", pager.currentItem)
    }

    class ImageViewerPager (private val mContext: Context) : PagerAdapter() {

        var imageView: ZoomableImageView? = null
        var pictures: List<Picture> = listOf()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int {
            return pictures.size
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj as ConstraintLayout
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {

            val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val itemView: View = inflater.inflate(R.layout.image_viewer_page, container, false)

            imageView = itemView.findViewById(R.id.image_view)

            if(position < pictures.size) {
                val picture = pictures[position]

                val imgUrl = picture.fullResUrl
                if(imgUrl.isNotEmpty()) {
                    Picasso.with(mContext).load(imgUrl).into(imageView)
                }

                (container as ViewPager).addView(itemView)
            }

            return itemView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            (container as ViewPager).removeView(`object` as ConstraintLayout?)
        }
    }
}