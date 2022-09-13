package fr.curlyspiker.jpics

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class SyncFragment : Fragment() {

    private lateinit var syncedList : RecyclerView

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

        syncedFoldersAdapter.refresh()
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
            visibleFolders = folders.filter { f -> !uploadMgr.isFolderIgnored(File(f.name).parent) }.toMutableList()
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
                uploadMgr.setFolderIgnored(InstantUploadManager.SyncFolder(folder.name, !uploadMgr.isFolderIgnored(folder)))
                refresh()
            }
            vh.syncIcon.setOnLongClickListener {
                val parentFolder = File(folder.name).parent!!
                uploadMgr.setFolderIgnored(InstantUploadManager.SyncFolder(parentFolder, !uploadMgr.isFolderIgnored(parentFolder)))
                refresh()
                true
            }
        }

        override fun getItemCount(): Int {
            return visibleFolders.size
        }
    }
}

