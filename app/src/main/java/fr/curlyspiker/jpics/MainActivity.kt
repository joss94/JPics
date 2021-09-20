package fr.curlyspiker.jpics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.Volley

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PiwigoServerHelper.initialize(Volley.newRequestQueue(this))

        setSupportActionBar(findViewById(R.id.my_toolbar))
    }
}