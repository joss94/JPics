package fr.curlyspiker.jpics

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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

    private lateinit var player: ExoPlayer

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

        view.findViewById<Toolbar>(R.id.my_toolbar).setOnMenuItemClickListener { item ->
            var out = true
            when (item?.itemId) {
                R.id.action_share -> (activity as MainActivity).shareImages(listOf(currentPicId))
                R.id.action_download -> (activity as MainActivity).downloadImages(listOf(currentPicId))
                R.id.action_move -> (activity as MainActivity).moveImages(null, listOf(currentPicId))
                R.id.action_delete -> (activity as MainActivity).archiveImages(listOf(currentPicId))
                R.id.action_info -> infoDialog.show()
                else -> out = false
            }
            out
        }

        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            findNavController().popBackStack()
        }

        pagerAdapter = ImageViewerPager(this)
        pager.adapter = pagerAdapter
        pager.offscreenPageLimit = 2

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
                val currentIdx = it.indexOfFirst { p -> p.picId == currentPicId }
                pager.currentItem = if (currentIdx != -1) currentIdx else min(oldIndex, pagerAdapter.count - 1)
                updateBottomSheet()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        player = ExoPlayer.Builder(requireActivity()).build()
    }

    override fun onPause() {
        super.onPause()
        player.release()
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

                val imageView = v.findViewById<ImageView>(R.id.image_view)
                val videoView = v.findViewById<StyledPlayerView>(R.id.video_view)
                videoView.visibility = View.GONE

                Picasso.with(ctx).load(pic.thumbnailUrl).into(imageView)

                imageView.setOnClickListener {
                    Log.d("PIVF", "Playing ${pic.elementUrl}")
                    videoView.player = fragment.player
                    fragment.player.clearMediaItems()
                    fragment.player.addMediaItem(MediaItem.fromUri(Uri.parse(pic.elementUrl)))
                    fragment.player.prepare()
                    fragment.player.playWhenReady = true
                    videoView.visibility = View.VISIBLE
                    imageView.visibility = View.INVISIBLE
                }

                v
            } else {
                val v: View = inflater.inflate(R.layout.image_viewer_page, container, false)

                val imageView = v.findViewById<ImageView>(R.id.image_view)
                Picasso.with(ctx).load(pic.thumbnailUrl).into(imageView)
                Picasso.with(ctx).load(pic.largeResUrl).into(imageView)
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