package fr.curlyspiker.jpics

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.findNavController


class AccountFragment : Fragment() {

    private lateinit var usernameText: TextView
    private lateinit var logoutButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        usernameText = view.findViewById(R.id.user)
        logoutButton = view.findViewById(R.id.logout_button)

        usernameText.text = PiwigoSession.user.username

        logoutButton.setOnClickListener {
            (activity as MainActivity).logout()
        }

        view.findViewById<TextView>(R.id.sync_button).setOnClickListener {
            findNavController().navigate(AccountFragmentDirections.actionAccountFragmentToSyncFragment())
        }

        view.findViewById<TextView>(R.id.archive_button).setOnClickListener {
            findNavController().navigate(AccountFragmentDirections.actionAccountFragmentToArchiveFragment())
        }

        view.findViewById<TextView>(R.id.settings_button).setOnClickListener {
            findNavController().navigate(AccountFragmentDirections.actionAccountFragmentToSettingsFragment())
        }
    }
}