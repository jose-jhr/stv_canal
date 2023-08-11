package com.ingenieria.stv

import android.R
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.ingenieria.stv.databinding.ActivityMainBinding
import kotlin.concurrent.thread


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

    private val TIME_VAL_ERROR = 10000L

    var trueConectionInternet = false

    var idFirebase = ""

    //db controll
    private var db = Firebase.firestore

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
        /**get id firebase**/
        notificationFirebase()
        /**init media controler**/
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

        //no block display
        lockDisplay()

        binding.playerView.setOnClickListener {
            copyToClipboard(this,idFirebase)
        }


        /** rx Firebase liveData **/
        MyFirebaseMessagingService.messageReceived.observe(this, Observer {message->
            println("llego mensaje ${message.notification?.title?:"tittle"}")
            Toast.makeText(this, "Mensaje recibido", Toast.LENGTH_SHORT).show()
            Log.d("firebasejhr",message.notification?.title?:"tittle observe")
            player.release()
            playUrl(message.notification?.body?:"")
        })


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
        /*
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
        */


       val dataTokens = db.collection("tokens").whereEqualTo("token",idFirebase).get()
        dataTokens.addOnSuccessListener {
            if(!it.isEmpty){
                /**itera elements result**/
                for (doc in it){
                    //Log.d("firebasejhr","reference ${doc.data["location"]}")
                    //Log.d("firebasejhr"," elementos $it")
                    url_defecto = "${doc.data["url_current"]}"
                    playUrl(url_defecto)
                    break
                }
                /**elements empty no found in the system**/
            }else{
               alertCreateElementFromFirestore()
            }
        }.addOnFailureListener {
            Toast.makeText(applicationContext, "Algo salio mal con la petición", Toast.LENGTH_SHORT).show()
        }

    }

    private fun alertCreateElementFromFirestore() {
        /**Create edit text for name db**/
        val edtLocation = EditText(this)
        edtLocation.hint = "Zona o lugar del dispositivo"

        /**Create AlertDialog for send text a firestore**/
        AlertDialog.Builder(this)
            .setTitle("¿Tu token no se encontro en BD deseas almacenarlo?")
            .setView(edtLocation)
            .setMessage("Ingresa por favor el lugar de este dispositivo sin espacios ni mayusculas") // Specifying a listener allows you to take an action before dismissing the dialog.
            // The dialog is automatically dismissed when a dialog button is clicked.
            .setPositiveButton(
                R.string.yes
            ) { dialog, which ->
                //data to send

                val dataTokens = db.collection("tokens").whereEqualTo("location","${edtLocation.text}").get()
                dataTokens.addOnSuccessListener {
                    if(it.isEmpty){
                        /**no found elements ok register**/
                        val tokenSend: MutableMap<String, Any> = HashMap()
                        tokenSend["location"] = "${edtLocation.text}"
                        tokenSend["url_current"] = "null"
                        tokenSend["token"] = "$idFirebase"

                        // Continue send data to firestore
                        db.collection("tokens").document()
                            .set(tokenSend).addOnSuccessListener {
                                Toast.makeText(applicationContext, "Se envio token correctamente, por favor ponle una url", Toast.LENGTH_LONG).show()
                            }.addOnFailureListener {
                                Toast.makeText(applicationContext, "Algo salio mal al enviar token", Toast.LENGTH_LONG).show()
                            }

                    }else{
                        Toast.makeText(this, "El lugar ${edtLocation.text} ya se encuentra registrado, cambialo por favor....", Toast.LENGTH_LONG).show()
                        /**elements empty no found in the system**/
                        alertCreateElementFromFirestore()
                    }
                }.addOnFailureListener {
                    Toast.makeText(applicationContext, "Algo salio mal con la petición", Toast.LENGTH_SHORT).show()
                }


            } // A null listener allows the button to dismiss the dialog and take no further action.
            .setNegativeButton(R.string.no, null)
            .setIcon(R.drawable.ic_dialog_alert)
            .show()
    }


}