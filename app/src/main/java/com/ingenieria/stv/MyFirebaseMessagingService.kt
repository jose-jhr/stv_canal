package com.ingenieria.stv

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.service.controls.ControlsProviderService.TAG
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.math.log

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MyFirebaseMessagingService:FirebaseMessagingService() {


    companion object{
        val messageReceived :MutableLiveData<RemoteMessage> = MutableLiveData()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        Log.d("firebasejhr","entramos")
        messageReceived.postValue(remoteMessage)
    }

}