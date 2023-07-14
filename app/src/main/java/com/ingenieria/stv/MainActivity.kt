package com.ingenieria.stv

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.android.volley.DefaultRetryPolicy
import com.android.volley.toolbox.StringRequest
import com.androidstudy.networkmanager.Monitor
import com.androidstudy.networkmanager.Tovuti
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceIdReceiver
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.messaging.FirebaseMessaging
import com.ingenieria.stv.databinding.ActivityMainBinding
import kotlin.concurrent.thread
import kotlin.math.log


class MainActivity : AppCompatActivity(){

    //var url_defecto = "http://192.168.0.104/local/repeat.m3u8"
    var url_defecto = "http://videosurnet.co/surnet/ricaurte/2.m3u8"

    var contador = 0

    lateinit var player:SimpleExoPlayer
    lateinit var mediaSource: HlsMediaSource
    lateinit var mediaSourceFactory: HlsMediaSource.Factory

    var repeatConexion = true

    lateinit var binding:ActivityMainBinding

    var errorAddString =""

    var playUrlVideo = false

    private val TIME_VAL_ERROR = 1000L

    var trueConectionInternet = false

    var idFirebase = ""



    @SuppressLint("JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        setContentView(binding.root)
        hideNotificationBar()
       // medController = MediaController(this)
       // videoView.setMediaController(medController)
        mediaPlayerController()



        Tovuti.from(this).monitor(object:Monitor.ConnectivityListener{
            override fun onConnectivityChanged(
                connectionType: Int,
                isConnected: Boolean,
                isFast: Boolean
            ) {
                trueConectionInternet = isConnected
                if (isConnected){
                    Toast.makeText(applicationContext,"Conectado a internet",Toast.LENGTH_SHORT).show()
                    playUrlVideo = true
                    contador ++
                    Toast.makeText(applicationContext, "Conectado $contador", Toast.LENGTH_SHORT).show()
                    getUrlServer()
                }else{
                    Toast.makeText(applicationContext, "Sin conexion a internet", Toast.LENGTH_SHORT).show()
                }
            }

        })
        /**test error url*/
        binding.btnError.visibility = View.GONE
        /*
        binding.btnError.visibility = View.GONE
        binding.btnError.setOnLongClickListener {
            if (errorAddString.isNotEmpty()){
                Toast.makeText(this, "error eliminado", Toast.LENGTH_SHORT).show()
                errorAddString = ""
            }else{
                Toast.makeText(this, "error adicionado", Toast.LENGTH_SHORT).show()
                errorAddString = "h"
            }
            true
        }*/

        //no block display
        lockDisplay()

        binding.playerView.setOnClickListener {
            copyToClipboard(this,idFirebase)
        }


        /** rx Firebase liveData **/
        MyFirebaseMessagingService.messageReceived.observe(this, Observer {message->
            println("llego mensaje ${message.notification?.title?:"tittle"}")
            Log.d("firebasejhr",message.notification?.title?:"tittle observe")
            player.release()
            playUrl(message.notification?.body?:"")
        })

        /**get id firebase**/
        notificationFirebase()
    }

    /**
     * copy notification id with click playerView(Exoplayer)
     */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "id copiado en portapapeles", Toast.LENGTH_SHORT).show()
    }



    private fun notificationFirebase() {
       FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
           if (!task.isSuccessful) {
               Log.d("firebasejhr", "token failed")
               return@addOnCompleteListener
           }
           val token = task.result
           Log.d("firebasejhr", token)
           binding.txtIdFirebase.text = token
           idFirebase = token
       }
    }

    /**
     * Lock no hide display
     */
    private fun lockDisplay() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyApp::MyWakelockTag")
        wakeLock.acquire(100000*60*1000L /*10 minutes*/)

    }

    private fun playUrl(toUrl: String) {

        /** Controll selector */
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSizeSd())

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setTargetBufferBytes(-1).createDefaultLoadControl()

        val renderesrFactory = DefaultRenderersFactory(this).setExtensionRendererMode(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        )

        player = ExoPlayerFactory.newSimpleInstance(this)

        mediaSource = mediaSourceFactory.createMediaSource(Uri.parse(toUrl))

        player.apply {
            prepare(mediaSource)
            playWhenReady = true
        }

        binding.playerView.player = player
        //player error retry
        playerAllListener()
    }

    private fun playerAllListener() {
        /** Error listener*/
        player.addListener(object : com.google.android.exoplayer2.Player.EventListener {
            override fun onPlayerError(error: com.google.android.exoplayer2.ExoPlaybackException) {
                if (trueConectionInternet){
                    thread(start = true) {
                        Thread.sleep(TIME_VAL_ERROR)
                        getUrlServer()
                    }
                }
            }
        })

        /** Listener play url */
        //listener de la reproduccion
        player.addListener(object : com.google.android.exoplayer2.Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                try {
                    when (playbackState) {
                        com.google.android.exoplayer2.Player.STATE_BUFFERING -> {
                            Toast.makeText(this@MainActivity, "Cargando", Toast.LENGTH_SHORT).show()
                            println("Cargando")
                        }
                        com.google.android.exoplayer2.Player.STATE_ENDED -> {
                            Toast.makeText(this@MainActivity, "Terminado", Toast.LENGTH_SHORT).show()
                            //mediaPlayerController()
                            getUrlServer()
                        }
                        com.google.android.exoplayer2.Player.STATE_IDLE -> {
                            Toast.makeText(this@MainActivity, "Inactivo", Toast.LENGTH_SHORT).show()

                        }
                        com.google.android.exoplayer2.Player.STATE_READY -> {
                            Toast.makeText(this@MainActivity, "Listo", Toast.LENGTH_SHORT).show()
                            repeatConexion = false
                        }
                    }
                }catch (e:Exception){
                    e.printStackTrace()
                    println("error a la hora de conectarse")
                }
            }

        })
    }







    /**
     * Create instance player and method listener used for response status playing
     */
    private fun mediaPlayerController() {
            player = ExoPlayerFactory.newSimpleInstance(this)
            val httpDataSourceFactory = DefaultHttpDataSourceFactory(
                Util.getUserAgent(this, "ExoPlayerDemo"),
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                true
            )
            mediaSourceFactory = HlsMediaSource.Factory(httpDataSourceFactory)
        //response exoplayer
    }





    fun hideNotificationBar(){
        //ocutar barra de notificaciones
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()

        //ocultar barra de navegacion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        //ocultar barra de notificaciones y navegacion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }


    fun getUrlServer() {
        player.release()
        val url = "https://narino.stvcanal.com.co/urlapp/url_app.php"
        //val url = "http://192.168.0.105/2023/stv-obs/url/url.php"
        var strinRequest = object : StringRequest(Method.GET,url, { res->
            playUrlVideo = false
            val respuesta = res.toString()
            if (respuesta != null){
                url_defecto = respuesta
                playUrl(url_defecto+errorAddString)
            }
        }, {error->
            playUrlVideo = false
                playUrl(url_defecto)
        }){
        }
        strinRequest.retryPolicy = DefaultRetryPolicy(1000, 1,2f)
        MySingleton.getInstance(this).addRequestQueue<String>(strinRequest)
    }





}