package com.ingenieria.stv

//import com.google.android.exoplayer2.ExoPlayerFactory
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging
import com.ingenieria.stv.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.jetbrains.annotations.Nullable
import java.net.URL
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(){

    //channels example
    //https://live.prostream.al/al/smil:tropojatv.smil/playlist.m3u8
    //https://narino.stvcanal.com:3436/hybrid/play.m3u8
    //https://fe.tring.al/delta/105/out/u/rdghfhsfhfshs.m3u8
    //URL POR DEFECTO
    private var URL_STREAM = "https://narino.stvcanal.com:3436/hybrid/play.m3u8"
    lateinit var vb:ActivityMainBinding

    var idFirebase = ""

    //exoplayer
    lateinit var player: ExoPlayer
    private lateinit var eventSource: EventSource

    private val TAG = "MainActivity"

    /**
     * Firestore ---------------------------------------------------
     */
    private val TAG_FIRESTORE = "FirestoreListener"
    private var db: FirebaseFirestore? = null
    private var registration: ListenerRegistration? = null
    @SuppressLint("JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        setContentView(vb.root)

        hideNotificationBar()
        /**get id firebase**/
        notificationFirebase()
        /**Player music**/
        playStream()

        //no block display
        lockDisplay()

        vb.exoPlayer.setOnClickListener {
            copyToClipboard(this,idFirebase)
        }


        /** rx Firebase liveData **/
        MyFirebaseMessagingService.messageReceived.observe(this, Observer {message->
            println("llego mensaje ${message.notification?.title?:"tittle"}")
            Toast.makeText(this, "Mensaje recibido", Toast.LENGTH_SHORT).show()
            Log.d("firebasejhr",message.notification?.title?:"tittle observe")
            //player.release()
            //playUrl(message.notification?.body?:"")
        })


        //client server
        //clienteServer()


        //db instance firestore
        db = FirebaseFirestore.getInstance()
        // Llamar a la función para escuchar cambios
        listenToTokensCollection();

    }

    private fun listenToTokensCollection() {
        registration = db!!.collection("tokens")
            .addSnapshotListener(object : EventListener<QuerySnapshot?> {
                override fun onEvent(
                    @Nullable snapshot: QuerySnapshot?,
                    @Nullable e: FirebaseFirestoreException?
                ) {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e)
                        return
                    }
                    if (snapshot != null) {
                        for (dc in snapshot.documentChanges) {
                            runOnUiThread {
                                Log.d(TAG_FIRESTORE, dc.document.data["url_current"].toString())
                                //Toast.makeText(this@MainActivity, "Cambios", Toast.LENGTH_SHORT).show()
                            }
                            val token = dc.document.data["token"].toString() == idFirebase
                            if (token){
                                URL_STREAM = dc.document.data["url_current"].toString()
                                updateStream(URL_STREAM)
                            }

//                            when (dc.type) {
//                                ADDED -> Log.d(TAG, "New document: " + dc.document.id)
//                                MODIFIED -> Log.d(TAG, "Modified document: " + dc.document.id)
//                                REMOVED -> Log.d(TAG, "Removed document: " + dc.document.id)
//                            }
                        }
                    }
                }
            })
    }

    private fun checkTokenInFirestore(token: String) {
        db!!.collection("tokens")
            .whereEqualTo("token", token)
            .get()
            .addOnCompleteListener { task: Task<QuerySnapshot> ->
                if (task.isSuccessful) {
                    var tokenExists = false
                    for (document in task.result) {
                        URL_STREAM = document.data["url_current"].toString()
                        updateStream(URL_STREAM)
                        Log.d(
                            TAG_FIRESTORE,
                            "NUEVA URL: " +URL_STREAM
                        )

                        Log.d(
                            TAG_FIRESTORE,
                            "Token found: " + document.id + " => " + document.data
                        )
                        tokenExists = true // El token existe
                    }
                    if (!tokenExists) {
                        Log.d(TAG_FIRESTORE, "Token not found in Firestore.")
                        alertSendForm()
                    }
                } else {
                    Log.w(TAG_FIRESTORE, "Error getting documents.", task.exception)
                }
            }
    }


    private fun alertSendForm() {
        //Inflate the dialog with custom view
        val mDialogView = LayoutInflater.from(this).inflate(R.layout.form_data_token, null)
        //AlertDialogBuilder
        val mBuilder = AlertDialog.Builder(this)
            .setView(mDialogView)
        //show dialog
        val  mAlertDialog = mBuilder.show()


        val editTextCity = mAlertDialog.findViewById<EditText>(R.id.editTextCity)
        val editTextAlias = mAlertDialog.findViewById<EditText>(R.id.editTextAlias)
        val btnSendServer = mAlertDialog.findViewById<Button>(R.id.btnSendServer)

        btnSendServer?.setOnClickListener {
            val isIncorrectData = arrayOf<Boolean>(false, false)

            if (editTextCity?.text.toString().isEmpty()) {
                isIncorrectData[0] = true
            }
            if (editTextAlias?.text.toString().isEmpty()) {
                isIncorrectData[1] = true
            }

            if (isIncorrectData[0]) {
                editTextCity?.error = "Completa este campo"
            }
            if (isIncorrectData[1]) {
                editTextCity?.error = "Complete este campo"
            }

            if (!isIncorrectData[0] && !isIncorrectData[1]) {
                val city = editTextCity?.text.toString().toUpperCase()
                val alias = editTextAlias?.text.toString()
                val key = idFirebase
                val urlCurrent = URL_STREAM

                // Aquí puedes enviar los datos a Firestore
                sendDataToFirestore(city, alias, key, urlCurrent)
                mAlertDialog.dismiss()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Complete los campos por favor",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        mAlertDialog.setCancelable(false)
        mAlertDialog.show()

//
//        val builder = AlertDialog.Builder(this)
//        val inflater = layoutInflater
//        val dialogLayout = inflater.inflate(R.layout.form_data_token, null)
//
//        val editTextCity = dialogLayout.findViewById<EditText>(R.id.editTextCity)
//        val editTextAlias = dialogLayout.findViewById<EditText>(R.id.editTextAlias)
//        val btnSendServer = dialogLayout.findViewById<Button>(R.id.btnSendServer)
//
//        val dialog = builder.create()
//
//        btnSendServer.setOnClickListener {
//            val isIncorrectData = arrayOf<Boolean>(false,false)
//
//            if (editTextCity.text.toString().isEmpty()){
//                isIncorrectData[0] =true
//            }
//            if (editTextAlias.text.toString().isEmpty()){
//                isIncorrectData[1] =true
//            }
//
//            if (isIncorrectData[0]){
//                editTextCity.error = "Completa este campo"
//            }
//            if (isIncorrectData[1]){
//                editTextCity.error = "Complete este campo"
//            }
//
//            if (!isIncorrectData[0] && !isIncorrectData[1]){
//                val city = editTextCity.text.toString()
//                val alias = editTextAlias.text.toString()
//                val key = idFirebase
//                val urlCurrent = URL_STREAM
//
//                // Aquí puedes enviar los datos a Firestore
//                sendDataToFirestore(city, alias, key, urlCurrent)
//                dialog.dismiss()
//            }else{
//                Toast.makeText(this@MainActivity, "Complete los campos por favor", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        builder.show()
    }

    private fun sendDataToFirestore(city: String, alias: String, key: String, urlCurrent: String) {
        val tokenData = hashMapOf(
            "location" to city,
            "nombre" to alias,
            "token" to key,
            "url_current" to urlCurrent/* Asegúrate de obtener el token que quieras aquí */
        )

        db?.collection("tokens")
            ?.add(tokenData)
            ?.addOnSuccessListener { documentReference ->
                Log.d(TAG_FIRESTORE, "Document added with ID: ${documentReference.id}")
            }
            ?.addOnFailureListener { e ->
                Log.w(TAG_FIRESTORE, "Error adding document", e)
            }

    }


    /**
     * Cliente Server
     */
    private fun clienteServer() {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()

        val request = Request.Builder()
            .url("http://192.168.1.28/2024/sse/controlstv/sse-control-stv/public/api/sse")
            .header("Accept", "application/json; q=0.5")
            .addHeader("Accept", "text/event-stream")
            .build()

        connectToEventSource(client, request)
    }

    /**
     * Player stream with URL
     */
    private fun playStream() {
        player = ExoPlayer.Builder(this).build()
        vb.exoPlayer.player = player

        //Crea un MediaItem con la url
        val mediaItem = MediaItem.fromUri(Uri.parse(URL_STREAM))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Updata stream URL
     */
    private fun updateStream(newUrl: String) {
        URL_STREAM = newUrl // Actualiza la URL

        // Detén la reproducción actual
        player.stop()

        // Crea un nuevo MediaItem con la nueva URL
        val mediaItem = MediaItem.fromUri(Uri.parse(URL_STREAM))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true // Inicia la reproducción
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
           //binding.txtIdFirebase.text = token
           idFirebase = token
           checkTokenInFirestore(idFirebase)
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


    fun hideNotificationBar(){
        //ocutar barra de notificaciones
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()

        //ocultar barra de navegacion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        //ocultar barra de notificaciones y navegacion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }


    /**
     * Conection SSE
     */
    private fun connectToEventSource(client: OkHttpClient, request: Request) {
        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                super.onOpen(eventSource, response)
                Log.d(TAG, "Connection Opened")
                this@MainActivity.eventSource = eventSource // Guardar la referencia
            }

            override fun onClosed(eventSource: EventSource) {
                super.onClosed(eventSource)
                Log.d(TAG, "Connection Closed")
                reconnect(client, request) // Intentar reconectar
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                super.onEvent(eventSource, id, type, data)
                Log.d(TAG, "On Event Received! Data -: $data")
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                super.onFailure(eventSource, t, response)
                Log.d(TAG, "On Failure -: ${response?.body}")
                reconnect(client, request) // Intentar reconectar
            }
        }

        eventSource = EventSources.createFactory(client)
            .newEventSource(request, eventSourceListener)
    }

    private fun reconnect(client: OkHttpClient, request: Request) {
        Log.d(TAG, "Attempting to reconnect...")
        lifecycleScope.launchWhenCreated {
            withContext(Dispatchers.IO) {
                // Esperar un tiempo antes de intentar reconectar
                delay(2000) // Espera de 2 segundos antes de reconectar
                connectToEventSource(client, request)
            }
        }
    }



}