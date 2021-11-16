package fr.curlyspiker.jpics

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*


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
    private var currentPicId : Int = -1

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
        pagerAdapter.pictures = CategoriesManager.currentlyDisplayedList.toList()

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageSelected(position: Int) {
                currentPicId = pagerAdapter.pictures[position]
                updateBottomSheet()
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
        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_name)?.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertStyle)
            builder.setTitle("Change picture name")

            val input = EditText(this)
            input.hint = "New name of picture"
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(currentPic().name)
            input.setTextColor(getColor(R.color.white))
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, _ ->
                val name = input.text.toString()
                PiwigoSession.setPictureInfo(currentPicId, name = name) {
                    updateBottomSheet()
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

            builder.show()
        }

        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_date)?.setOnClickListener {
            Utils.getDatetime(supportFragmentManager, currentPic().creationDate) {
                PiwigoSession.setPictureInfo(currentPicId, creationDate = it) {
                    updateBottomSheet()
                }
            }
        }

        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_keywords)?.setOnClickListener {
            val dialog = TagEditor(this) { tags ->
                val newTags = tags.filter { t -> t.id == -1 }
                PiwigoSession.addTags(newTags) {
                    tags.forEach { t -> t.id = PiwigoSession.getTagFromString(t.name)?.id ?: t.id }
                    PiwigoSession.setPictureInfo(currentPicId, tags = tags) {
                        updateBottomSheet()
                    }
                }
            }

            dialog.setTags(currentPic().getTags())
            dialog.show()
        }

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

        currentPicId = pagerAdapter.pictures[index]
        pager.currentItem = index
        updateBottomSheet()
    }

    private fun currentPic() : Picture {
        return CategoriesManager.pictures.getValue(currentPicId)
    }

    private fun updateBottomSheet() {
        CategoriesManager.pictures[currentPicId]?.getInfo(true) { info ->
            runOnUiThread {
                val name = info.optString("name", "null")
                infoFilename.text = if(name == "null") info.optString("file", "Unknown") else name

                val creationString = info.optString("date_creation", "")
                try {
                    val creationDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(creationString) ?: Date()
                    val format = SimpleDateFormat("EEE dd MMMM yyyy HH:mm:ss", Locale.US)
                    infoCreationDate.text = format.format(creationDate)
                } catch (e: java.lang.Exception) {}



                var albumsTxt = ""
                currentPic().getCategories().forEach { c -> albumsTxt += "${c.name} - " }
                infoAlbums.text = if(albumsTxt.isEmpty()) "None" else albumsTxt.subSequence(0, albumsTxt.length - 3)

                infoSize.text = getString(R.string.size_info).format(info.optString("width", "0").toInt(), info.optString("height", "0").toInt())

                infoAddedBy.text = PiwigoSession.getUser(info.optString("added_by", "-1").toInt())?.username

                val tags = currentPic().getTags()
                var tagsTxt = ""
                tags.forEach { t ->
                    tagsTxt += t.name + " - "
                }
                infoTags.text = if(tagsTxt.isEmpty()) "None" else tagsTxt.subSequence(0, tagsTxt.length - 3)
            }
        }
    }

    private fun downloadImage() {
        val picture = CategoriesManager.pictures.getValue(pagerAdapter.pictures[pager.currentItem])
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

        val builder = AlertDialog.Builder(this, R.style.AlertStyle)
        builder.setTitle("What do you want to do ?")
        var checkedItem = 0
        val labels = arrayOf("Add to other album", "Move to different location")
        builder.setSingleChoiceItems(labels, checkedItem) { _, i -> checkedItem = i }
        builder.setCancelable(true)

        builder.setPositiveButton("OK") { _, _ ->
            selectCategory { c ->
                c?.let {
                    PiwigoSession.movePictures(listOf(picture), checkedItem == 0, it) {
                        CategoriesManager.refreshAllPictures {
                            runOnUiThread {
                                pagerAdapter.pictures = CategoriesManager.currentlyDisplayedList?.toList() ?: listOf()
                            }
                            updateBottomSheet()
                        }
                    }
                }
            }
        }

        builder.create().show()
    }

    private fun deleteImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Delete image")
            .setMessage("Are you sure you want to delete this image?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ -> PiwigoSession.deletePictures(listOf(picture)) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showInfo() {
        infoDialog.show()
    }

    private fun selectCategory(callback: (cat: Category) -> Unit) {
        val dialog = CategoryPicker(this)
        dialog.setOnCategorySelectedCallback(callback)
        dialog.show()
    }

    override fun onSaveInstanceState (outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("img_index", pager.currentItem)
    }

    class ImageViewerPager (private val mContext: Context) : PagerAdapter() {

        private var imageView: ZoomableImageView? = null
        var pictures: List<Int> = listOf()
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
                val id = pictures[position]

                val pic = CategoriesManager.pictures.getValue(id)
                val imgUrl = pic.fullResUrl
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