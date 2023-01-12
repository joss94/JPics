package fr.curlyspiker.jpics

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min


class LogFragment : Fragment() {

    private lateinit var logListView: RecyclerView
    private var logsAdapter: LogsAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logListView = view.findViewById(R.id.log_list_view)
        logsAdapter = LogsAdapter(this)
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        logListView.layoutManager = layoutManager
        logListView.adapter  = logsAdapter

        view.findViewById<Button>(R.id.clear_logs_button).setOnClickListener {
            Utils.clearLog(requireContext())
            refreshLogs()
        }

        LogManager.logsChangedCallback = {
            refreshLogs()
        }

        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        LogManager.logsChangedCallback = {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    private fun refreshLogs() {
        val logs = Utils.readLog(requireContext()).split("\n")
        logsAdapter?.setLogs(logs.reversed())
    }

    class LogsAdapter(private val fragment: LogFragment) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(){

        private var items = listOf<LogManager.LogItem>()

        fun setLogs(logs: List<String>) {

            val newItems = mutableListOf<LogManager.LogItem>()
            logs.forEach { l -> newItems.add(LogManager.LogItem(l)) }
            fragment.activity?.runOnUiThread {
                items = newItems
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View =  LayoutInflater.from(fragment.requireContext()).inflate(R.layout.log_message, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, p0: Int) {
            val item = items[p0]
            (holder as LogViewHolder).message.text = item.message.take(1000)
            if (item.message.length > 1000) {
                var i = 0
                while (i < item.message.length) {
                    Log.d("test", item.message.substring(i, min(item.message.length, i+400)))
                    i += 400
                }

            }
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    class LogViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var message: TextView = itemView.findViewById(R.id.message)
    }
}