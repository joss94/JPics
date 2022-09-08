package fr.curlyspiker.jpics

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView

import android.view.*
import android.widget.*
import com.google.android.flexbox.*
import android.widget.ArrayAdapter





class TagEditor(context: Context, val callback: (List<PicTag>) -> Unit) : Dialog(context, R.style.AlertStyle) {

    private lateinit var tagsView: RecyclerView
    private lateinit var tagsAdapter: TagsAdapter
    private lateinit var tagTextView: AutoCompleteTextView

    private var tags = mutableListOf<PicTag>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_tag_picker)

        findViewById<Button>(R.id.cancel_button).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.ok_button).setOnClickListener {
            callback(tagsAdapter.getTags())
            dismiss()
        }

        tagsView = findViewById(R.id.tags_list_view)
        tagsAdapter = TagsAdapter(this)
        val layoutManager = FlexboxLayoutManager(context)
        layoutManager.flexDirection = FlexDirection.ROW
        layoutManager.justifyContent = JustifyContent.FLEX_START
        layoutManager.flexWrap = FlexWrap.WRAP
        tagsView.layoutManager = layoutManager
        tagsView.adapter  = tagsAdapter

        tagTextView = findViewById(R.id.tag_text_view)
        val tagsNames =  PiwigoData.getAllTags().map { t -> t.name }
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, tagsNames)
        tagTextView.setAdapter(adapter)
        findViewById<Button>(R.id.tag_edit_text_ok_button).setOnClickListener {
            val tagName = tagTextView.text.toString()
            if(tags.firstOrNull { t -> t.name == tagName } == null) {
                tags.add(PicTag(PiwigoData.getTagFromName(tagName)?.tagId ?: -1, tagName))
                tagsAdapter.setTags(tags)
            }
            tagTextView.setText("")
        }

        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        val back = ColorDrawable(Color.TRANSPARENT)
        val inset = InsetDrawable(back, 100, 0, 100, 0)
        window?.setBackgroundDrawable(inset)

        tagsAdapter.setTags(tags)
    }

    fun setTags(t: List<Int>) {
        tags = PiwigoData.getAllTags().filter { t.contains(it.tagId) }.toMutableList()
        if(this::tagsAdapter.isInitialized) {
            tagsAdapter.setTags(tags)
        }
    }

    class TagsAdapter(private val picker: TagEditor) :
        RecyclerView.Adapter<TagsAdapter.ViewHolder>(){

        private var tags = mutableListOf<PicTag>()

        fun setTags(t: List<PicTag>) {
            tags = t.toMutableList()
            notifyDataSetChanged()
        }

        fun getTags() : List<PicTag> {
            return tags
        }

        class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
            val tagName: TextView = view.findViewById(R.id.tag_name)
            val deleteTagButton: ImageButton = view.findViewById(R.id.delete_tag_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(picker.context).inflate(R.layout.tag_tile, parent, false))
        }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            val tag = tags[position]
            vh.tagName.text = tag.name
            vh.deleteTagButton.setOnClickListener {
                tags.removeAt(position)
                notifyDataSetChanged()
            }

            val lp: ViewGroup.LayoutParams = vh.itemView.layoutParams
            if (lp is FlexboxLayoutManager.LayoutParams) {
                lp.flexGrow = 1.0f
            }
        }

        override fun getItemCount(): Int {
            return tags.size
        }
    }

}