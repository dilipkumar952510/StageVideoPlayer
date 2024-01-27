package com.stage.videoplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.stage.videoplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var qualityList = mutableListOf<String>()
    private var qualityForShow = mutableListOf<String>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var exoPlayer: SimpleExoPlayer
    private var playbackPosition: Long = 0
    private var url = "https://vp.nyt.com/video/hls/2023/08/01/110343_1_opdoc-clean_wg/master.m3u8"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.click =ClickAction()
        setVideoPlayer()        // start the player and initialize first time
    }

    private fun setVideoPlayer() {
        exoPlayer = SimpleExoPlayer.Builder(this)           // initializing simple exoplayer with default track means auto resolution
            .setTrackSelector(DefaultTrackSelector(this))
            .setLoadControl(DefaultLoadControl())
            .build()

        playVideo()

        setVideoQualityAccordingNetwork()

        binding.resolutionSpinner.onItemSelectedListener = object : OnItemSelectedListener{
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (p2!=0){
                    changeVideoQuality(qualityList[p2-1])
                }else {
                    val trackSelector = exoPlayer.trackSelector as DefaultTrackSelector
                    trackSelector.parameters = DefaultTrackSelector(this@MainActivity).parameters
                    exoPlayer.playWhenReady = true
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun getAvailableResolutions(): List<String> {  // it will return the all resolution of video which are available in HLS url
        val resolutions = mutableListOf<String>()

        for (groupIndex in 0 until exoPlayer.currentTrackGroups.length) {
            val trackGroup = exoPlayer.currentTrackGroups[groupIndex]
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                if (MimeTypes.isVideo(format.sampleMimeType)) {
                    val resolution = "${format.width}x${format.height}"
                    resolutions.add(resolution)
                }
            }
        }
        return resolutions.distinct()
    }

    private fun playVideo(){
        binding.exoplayer.player = exoPlayer    // player-view will initialize with simple exoplayer

        // now preparing the new media source with url
        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, getString(R.string.app_name)))
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))

        exoPlayer.prepare(hlsMediaSource)

        exoPlayer.seekTo(playbackPosition)  // Restore the playback position
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.EventListener {               // used for get video resolution when video is ready
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    getVideoQuality()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Toast.makeText(this@MainActivity, error.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun changeVideoQuality(resolution: String) {        //it will change the video quality according to our choice
        playbackPosition = exoPlayer.currentPosition

        val trackSelector = exoPlayer.trackSelector as DefaultTrackSelector
        var parameters = trackSelector.parameters

        val videoRendererIndex = 0
        val trackGroups = (exoPlayer.trackSelector as MappingTrackSelector).currentMappedTrackInfo!!.getTrackGroups(videoRendererIndex)

        for (groupIndex in 0 until trackGroups.length) {
            val trackGroup = trackGroups.get(groupIndex)
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                if (MimeTypes.isVideo(format.sampleMimeType) && "${format.width}x${format.height}" == resolution) {
                    val trackSelection =
                        DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)
                    parameters =
                        parameters.buildUpon().setSelectionOverride(videoRendererIndex, trackGroups, trackSelection)
                            .build()
                }
            }
        }

        // Release the current player instance and create a new one
        exoPlayer.release()
        exoPlayer = SimpleExoPlayer.Builder(this)       // again simple player will initialize with new resolution
            .setTrackSelector(trackSelector)
            .setLoadControl(DefaultLoadControl())
            .build()

        playVideo()     // again calling hls url to play
    }

    private fun getVideoQuality() {
        qualityList = getAvailableResolutions().toMutableList()  // it will return the all resolution of video which are available in HLS url
        qualityList.sortByDescending { resolution ->
            val (width, height) = resolution.split("x").map { it.toInt() }
            width * height
        }
        qualityForShow.clear()
        qualityForShow.add("Auto")
        for(i in qualityList.indices){
            qualityForShow.add(getHeightFromResolution(qualityList[i]))
        }
        binding.resolutionSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, qualityForShow)
    }

    fun getHeightFromResolution(resolution: String): String {
        val dimensions = resolution.split("x")
        if (dimensions.size == 2) {
            val width = dimensions[0].toIntOrNull()
            val height = dimensions[1].toIntOrNull()

            if (width != null && height != null) {
                return height.toString()
            }
        }
        return ""
    }

    private fun setVideoQualityAccordingNetwork() {  // this will set your player quality according to network type
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (networkCapabilities != null) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // in this situation video quality will be high
                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                // in this situation video quality will be low
                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
        }
    }

    fun changeOrientation() {   // it will used for orientation change when portrait then search panel will otherwise video will show on full screen
        val currentOrientation = resources.configuration.orientation

        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            binding.searchLayout.isVisible = false
            binding.heading.isVisible = false
            binding.fullScreen.setImageResource(R.drawable.exit_full_screen)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            binding.searchLayout.isVisible = true
            binding.heading.isVisible = true
            binding.fullScreen.setImageResource(R.drawable.full_screen)
        }
    }


    inner class ClickAction{
        fun onSearch(view: View){
            if (binding.search.text.toString().trim().isNotEmpty()) {
                url = binding.search.text.toString().trim()      // it will use for search but please enter only HLS url
                exoPlayer.release()
                setVideoPlayer()
            }
        }

        fun onRotate(view: View){
            changeOrientation()
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer.pause()
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer.playWhenReady = true
    }
}