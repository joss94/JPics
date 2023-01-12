package fr.curlyspiker.jpics

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView

class VideoViewerActivity : AppCompatActivity() {

    private lateinit var player : ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_viewer)

        player = ExoPlayer.Builder(this).build()

        val url = intent.getStringExtra("url")

        val videoView = findViewById<StyledPlayerView>(R.id.video_view)
        videoView.player = player
        player.clearMediaItems()
        player.addMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }
}