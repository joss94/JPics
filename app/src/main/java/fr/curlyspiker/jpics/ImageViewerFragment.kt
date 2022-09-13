package fr.curlyspiker.jpics

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


class ImageViewerFragment : Fragment() {


    private val mainActivityVM: MainActivityViewModel by activityViewModels()

    private lateinit var pager : ViewPager
    private lateinit var pagerAdapter: ImageViewerFragment.ImageViewerPager

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_image_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pager = view.findViewById(R.id.pager)

        catId = mainActivityVM.getCatId() ?: -1
        currentPicId = mainActivityVM.getPicId() ?: -1

        pagerAdapter = ImageViewerPager(requireContext())
        pager.adapter = pagerAdapter
        pagerAdapter.pictures = PiwigoData.currentlyDisplayedList.toList()

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageSelected(position: Int) {
                currentPicId = pagerAdapter.pictures[position]
                updateBottomSheet()
            }
        })

        infoDialog = BottomSheetDialog(requireContext())
        infoDialog.setContentView(R.layout.dialog_image_info)

        infoFilename = infoDialog.findViewById(R.id.image_name)!!
        infoCreationDate = infoDialog.findViewById(R.id.image_creation_date)!!
        infoAlbums = infoDialog.findViewById(R.id.albums)!!
        infoSize = infoDialog.findViewById(R.id.size)!!
        infoAddedBy = infoDialog.findViewById(R.id.info_addedby)!!
        infoTags = infoDialog.findViewById(R.id.info_keywords)!!
        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_name)?.setOnClickListener {
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            builder.setTitle("Change picture name")

            val input = EditText(requireContext())
            input.hint = "New name of picture"
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(currentPic()?.name ?: "")
            input.setTextColor(requireContext().getColor(R.color.white))
            builder.setView(input)

            builder.setPositiveButton("OK") { dialog, _ ->
                val name = input.text.toString()
                lifecycleScope.launch (Dispatchers.IO){
                    PiwigoData.setPicName(currentPicId, name)
                    updateBottomSheet()
                }

                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

            builder.show()
        }

        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_date)?.setOnClickListener {
            currentPic()?.let { pic ->
                Utils.getDatetime(requireActivity().supportFragmentManager, pic.creationDate) { d ->
                    lifecycleScope.launch (Dispatchers.IO){
                        PiwigoData.setPicCreationDate(currentPicId, creationDate = Date(d))
                        updateBottomSheet()
                    }
                }
            }
        }

        infoDialog.findViewById<ImageButton>(R.id.pic_info_edit_keywords)?.setOnClickListener {
            currentPic()?.let { pic ->
                val dialog = TagEditor(requireContext()) { tags ->
                    val newTags = tags.filter { t -> t.tagId == -1 }
                    lifecycleScope.launch (Dispatchers.IO){
                        PiwigoData.addTags(newTags)
                        val tagIds = tags.mapNotNull { t ->  PiwigoData.getTagFromName(t.name)?.tagId }
                        PiwigoData.setPicTags(currentPicId, tagIds)
                        updateBottomSheet()
                    }
                }

                dialog.setTags(pic.getTags())
                dialog.show()
            }
        }

        requireActivity().startActionMode(object : ActionMode.Callback{
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val menuInflater: MenuInflater? = mode?.menuInflater
                menuInflater?.inflate(R.menu.viewer_menu, menu)
                return true
            }

            override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean { return true }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                var out = true
                when (item?.itemId) {
                    R.id.action_share -> shareImage()
                    R.id.action_download -> downloadImage()
                    R.id.action_move -> moveImage()
                    R.id.action_delete -> deleteImage()
                    R.id.action_info -> showInfo()
                    else -> out = false
                }
                return out
            }

            override fun onDestroyActionMode(p0: ActionMode?) {
                //finish()
                //TODO: Fix bug
            }
        })

        pager.currentItem = pagerAdapter.pictures.indexOf(currentPicId)
        updateBottomSheet()

    }

    private fun currentPic() : Picture? {
        return PiwigoData.getPictureFromId(currentPicId)
    }

    private fun updateBottomSheet() {
        currentPic()?.let { pic ->
            pic.getInfo(true) { info ->
                activity?.runOnUiThread {
                    val name = info.optString("name", "null")
                    infoFilename.text = if(name == "null") info.optString("file", "Unknown") else name

                    val creationString = info.optString("date_creation", "")
                    try {
                        val creationDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(creationString) ?: Date()
                        infoCreationDate.text = SimpleDateFormat("EEE dd MMMM yyyy HH:mm:ss", Locale.US).format(creationDate)
                    } catch (e: java.lang.Exception) {}



                    var albumsTxt = ""
                    PiwigoData.getPictureCategories(currentPicId).forEach { id -> albumsTxt += "${PiwigoData.getCategoryFromId(id)?.name} - " }
                    infoAlbums.text = if(albumsTxt.isEmpty()) "None" else albumsTxt.subSequence(0, albumsTxt.length - 3)

                    infoSize.text = getString(R.string.size_info).format(info.optString("width", "0").toInt(), info.optString("height", "0").toInt())

                    infoAddedBy.text = PiwigoData.getUserFromId(info.optString("added_by", "-1").toInt())?.username

                    val tags = pic.getTags()
                    var tagsTxt = ""
                    tags.forEach { t ->
                        tagsTxt += PiwigoData.getTagFromId(t)?.name + " - "
                    }
                    infoTags.text = if(tagsTxt.isEmpty()) "None" else tagsTxt.subSequence(0, tagsTxt.length - 3)
                }
            }
        }
    }

    private fun shareImage() {
        val path = requireActivity().cacheDir.absolutePath + File.separator + "/images"
        val target = object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                try {
                    val folder = File(path)
                    if(!folder.exists()) { folder.mkdirs() }
                    val file = File(folder.path + File.separator + "image_0.jpg")
                    file.createNewFile()
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    stream.close()

                    val contentUri = FileProvider.getUriForFile(
                        requireContext(),
                        "fr.curlyspiker.jpics.fileprovider",
                        file
                    )
                    val shareIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        type = requireContext().contentResolver.getType(contentUri)
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                    }
                    try {
                        startActivity(Intent.createChooser(shareIntent, "Select sharing app"))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), "No App Available", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onBitmapFailed(errorDrawable: Drawable?) {
                Log.d("TAG", "Error during download !")
            }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        }

        currentPic()?.let { pic ->
            Picasso.with(requireContext()).load(pic.thumbnailUrl).into(target)
            Picasso.with(requireContext()).load(pic.largeResUrl).into(target)
        }
    }

    private fun downloadImage() {
        val picture = PiwigoData.getPictureFromId(pagerAdapter.pictures[pager.currentItem])
        fun downloadImagesPermissionGranted() {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            picture?.saveToLocal(requireContext(), path)
        }

        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            onPermissionsGranted = { downloadImagesPermissionGranted() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun moveImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]

        val builder = AlertDialog.Builder(requireContext(), R.style.AlertStyle)
        builder.setTitle("What do you want to do ?")
        var checkedItem = 0
        val labels = arrayOf("Add to other album", "Move to different location")
        builder.setSingleChoiceItems(labels, checkedItem) { _, i -> checkedItem = i }
        builder.setCancelable(true)

        builder.setPositiveButton("OK") { _, _ ->
            selectCategory { c ->
                if(checkedItem == 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        PiwigoData.addPicsToCats(listOf(picture), listOf(c))
                        pagerAdapter.pictures = PiwigoData.currentlyDisplayedList.toList()
                        updateBottomSheet()
                    }
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        PiwigoData.movePicsToCat(listOf(picture), c)
                        pagerAdapter.pictures = PiwigoData.currentlyDisplayedList.toList()
                        updateBottomSheet()
                    }
                }
            }
        }

        builder.create().show()
    }

    private fun deleteImage() {
        val picture = pagerAdapter.pictures[pager.currentItem]
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Delete image")
            .setMessage("Are you sure you want to delete this image?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.archivePictures(listOf(picture), true)
                }
                //finish()
                //TODO: Fix bug
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showInfo() {
        infoDialog.show()
    }

    private fun selectCategory(callback: (cat: Int) -> Unit) {
        val dialog = CategoryPicker(requireContext())
        dialog.setOnCategorySelectedCallback(callback)
        dialog.show()
    }


    class ImageViewerPager (private val mContext: Context) : PagerAdapter() {

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

            val id = pictures[position]
            val pic = PiwigoData.getPictureFromId(id)!!

            val itemView =  if (pic.isVideo()) {
                val v: View = inflater.inflate(R.layout.image_viewer_page_video, container, false)
                val videoView = v.findViewById<ImageView>(R.id.video_view)
                Picasso.with(mContext).load(pic.largeResUrl).into(videoView)
                videoView?.setOnClickListener {
                    Log.d("TAG", "Trying to start something")
                    val intent = Intent(mContext, VideoViewerActivity::class.java)
                    intent.putExtra("url", pic.elementUrl)
                    ContextCompat.startActivity(mContext, intent, null)
                }
                v
            } else {
                val v: View = inflater.inflate(R.layout.image_viewer_page, container, false)
                Picasso.with(mContext).load(pic.largeResUrl).into(v.findViewById<ZoomableImageView>(R.id.image_view))
                v
            }
            (container as ViewPager).addView(itemView)
            return itemView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            (container as ViewPager).removeView(`object` as ConstraintLayout?)
        }

    }
}