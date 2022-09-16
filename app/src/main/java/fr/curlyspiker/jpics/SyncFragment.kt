package fr.curlyspiker.jpics

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SyncFragment : Fragment() {

    private lateinit var syncedList : RecyclerView
    private lateinit var defaultAlbumText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_sync, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        syncedList = view.findViewById(R.id.synced_folders_list_view)
        val syncedFoldersAdapter = SyncedFolderAdapter(requireContext())
        syncedList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        syncedList.adapter  = syncedFoldersAdapter

        val defaultAlbumLayout = view.findViewById<LinearLayout>(R.id.instant_upload_cat_layout)
        defaultAlbumLayout.setOnClickListener {
            (activity as MainActivity).selectCategory { c ->
                val prefs = requireActivity().getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
                prefs.edit().putInt("default_album", c).apply()
                InstantUploadManager.getInstance(requireContext()).setDefaultCategory(c)
                refreshDefaultLabel()
            }
        }

        defaultAlbumText = view.findViewById<TextView>(R.id.instant_upload_cat_title)
        refreshDefaultLabel()

        syncedFoldersAdapter.refresh()
    }

    private fun refreshDefaultLabel() {
        val prefs = requireActivity().getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
        val defaultAlbumId = prefs.getInt("default_album", -1)
        lifecycleScope.launch(Dispatchers.IO) {
            PiwigoData.getCategoryFromId(defaultAlbumId)?.let {
                defaultAlbumText.text = it.name
            }
        }
    }


    class SyncedFolderAdapter(val context: Context) :
        RecyclerView.Adapter<SyncedFolderAdapter.ViewHolder>(){

        private val uploadMgr = InstantUploadManager.getInstance(context)
        private var folders = listOf<InstantUploadManager.SyncFolder>()
        private var visibleFolders = mutableListOf<InstantUploadManager.SyncFolder>()

        class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.sync_folder_title)
            val path: TextView = view.findViewById(R.id.sync_folder_path)
            val syncIcon: ImageButton = view.findViewById(R.id.sync_icon)
        }

        fun refresh() {
            folders = uploadMgr.getAllFolders()
            visibleFolders = folders.toMutableList()
            //visibleFolders = folders.filter { f -> !uploadMgr.isFolderIgnored(File(f.name).parent) }.toMutableList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sync_folder_row, parent, false))
        }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            val folder = visibleFolders[position]
            vh.title.text = Uri.parse(folder.name).lastPathSegment
            vh.path.text = File(folder.name).parent
            vh.syncIcon.background = AppCompatResources.getDrawable(context, if(uploadMgr.isFolderSynced(folder.name)) R.drawable.cloud_on else R.drawable.cloud_off)
            vh.syncIcon.setOnClickListener {
                uploadMgr.setFolderIgnored(folder.name, !uploadMgr.isFolderIgnored(folder))
                refresh()
            }
            vh.syncIcon.setOnLongClickListener {
                val parentFolder = File(folder.name).parent!!
                uploadMgr.setFolderIgnored(parentFolder, !uploadMgr.isFolderIgnored(parentFolder))
                refresh()
                true
            }
        }

        override fun getItemCount(): Int {
            return visibleFolders.size
        }
    }
}

