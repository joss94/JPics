package fr.curlyspiker.jpics

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Integer.max
import java.lang.Integer.min
import java.text.SimpleDateFormat
import java.util.*


class ImageViewerFragment : Fragment() {


    private val mainActivityVM: MainActivityViewModel by activityViewModels()

    private lateinit var pager : ViewPager
    private lateinit var pagerAdapter: ImageViewerPager

    private lateinit var infoDialog: BottomSheetDialog

    private lateinit var infoFilename : TextView
    private lateinit var infoCreationDate : TextView
    private lateinit var infoAlbums : TextView
    private lateinit var infoSize : TextView
    private lateinit var infoAddedBy : TextView
    private lateinit var infoTags : TextView

    private var currentPicId : Int = -1

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
            (activity as MainActivity).editImagesCreationDate(listOf(currentPicId))
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
                    R.id.action_share -> currentPic()?.let { (activity as MainActivity).shareImages(listOf(it)) }
                    R.id.action_download -> (activity as MainActivity).downloadImages(listOf(currentPicId))
                    R.id.action_move -> (activity as MainActivity).moveImages(null, listOf(currentPicId))
                    R.id.action_delete -> (activity as MainActivity).archiveImages(listOf(currentPicId))
                    R.id.action_info -> infoDialog.show()
                    else -> out = false
                }
                mode?.finish()
                return out
            }

            override fun onDestroyActionMode(p0: ActionMode?) {}
        })

        pagerAdapter = ImageViewerPager(this)
        pager.adapter = pagerAdapter

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageSelected(position: Int) {
                currentPicId = pagerAdapter.pictures[position].picId
                updateBottomSheet()
            }
        })

        mainActivityVM.getDisplayedPics().observe(viewLifecycleOwner) {
            if (it.isEmpty()) {
                findNavController().popBackStack()
            } else {
                val oldIndex = pager.currentItem
                pagerAdapter.pictures = it
                pager.adapter = pagerAdapter

                currentPicId = mainActivityVM.getPicId() ?: -1
                val currentIndex = it.indexOfFirst { p -> p.picId == currentPicId }
                if (currentIndex != -1) {
                    pager.currentItem = currentIndex
                } else {
                    pager.currentItem = min(oldIndex, pagerAdapter.count - 1)
                }

                updateBottomSheet()
            }
        }
    }

    private fun currentPic() : Picture? {
        return PiwigoData.getPictureFromId(currentPicId)
    }

    private fun updateBottomSheet() {
        currentPic()?.let { pic ->
            infoFilename.text = pic.name
            infoCreationDate.text = SimpleDateFormat("EEE dd MMMM yyyy HH:mm:ss", Locale.US).format(pic.creationDate)

            var albumsTxt = ""
            PiwigoData.getPictureCategories(currentPicId).forEach { id -> albumsTxt += "${PiwigoData.getCategoryFromId(id)?.name} - " }
            infoAlbums.text = if(albumsTxt.isEmpty()) "None" else albumsTxt.subSequence(0, albumsTxt.length - 3)

            val tags = pic.getTags()
            var tagsTxt = ""
            tags.forEach { t ->
                tagsTxt += PiwigoData.getTagFromId(t)?.name + " - "
            }
            infoTags.text = if(tagsTxt.isEmpty()) "None" else tagsTxt.subSequence(0, tagsTxt.length - 3)

            lifecycleScope.launch(Dispatchers.IO) {
                val info = PiwigoData.getPictureInfo(currentPicId)
                activity?.runOnUiThread {
                    infoSize.text = getString(R.string.size_info).format(info.optString("width", "0").toInt(), info.optString("height", "0").toInt())
                    infoAddedBy.text = PiwigoData.getUserFromId(info.optString("added_by", "-1").toInt())?.username
                }
            }
        }
    }

    class ImageViewerPager (private val fragment: ImageViewerFragment) : PagerAdapter() {

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

            val ctx = fragment.requireContext()
            val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val pic = pictures[position]

            val itemView =  if (pic.isVideo()) {
                val v: View = inflater.inflate(R.layout.image_viewer_page_video, container, false)
                val videoView = v.findViewById<ImageView>(R.id.video_view)
                Picasso.with(ctx).load(pic.largeResUrl).into(videoView)
                videoView?.setOnClickListener {
                    Log.d("TAG", "Trying to start something")
                    val intent = Intent(ctx, VideoViewerActivity::class.java)
                    intent.putExtra("url", pic.elementUrl)
                    ContextCompat.startActivity(ctx, intent, null)
                }
                v
            } else {
                val v: View = inflater.inflate(R.layout.image_viewer_page, container, false)
                Picasso.with(ctx).load(pic.largeResUrl).into(v.findViewById<ZoomableImageView>(R.id.image_view))
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