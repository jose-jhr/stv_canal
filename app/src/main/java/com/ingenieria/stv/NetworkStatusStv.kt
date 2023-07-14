package com.ingenieria.stv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Unavailable : NetworkStatus()
}

class NetworkStatusStv(context: Context): LiveData<NetworkStatus>() {

    //establecemos conexion con el sistema atraves del cual se obtiene el estado de la red
    val connectivityManager:ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private lateinit var connectivityManagerCallback:ConnectivityManager.NetworkCallback

    override fun onActive() {
        super.onActive()
        connectivityManagerCallback = getConnectivityManagerCallback()
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, connectivityManagerCallback)
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(connectivityManagerCallback)
    }

    fun getConnectivityManagerCallback()=

        object : ConnectivityManager.NetworkCallback(){
            val valideNetworkConnections : ArrayList<Network> = ArrayList()

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                val hasNetworkConnection  = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)?:false
                if (hasNetworkConnection){
                    determineInternetAccess(network)
                    //valideNetworkConnections.add(network)
                    //announceStatus()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                valideNetworkConnections.remove(network)
                announceStatus()
            }

            fun announceStatus(){
                if (valideNetworkConnections.isNotEmpty()){
                    postValue(NetworkStatus.Available)
                } else {
                    postValue(NetworkStatus.Unavailable)
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                if(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)){
                    valideNetworkConnections.add(network)
                    announceStatus()
                } else {
                    valideNetworkConnections.remove(network)
                    announceStatus()
                }
            }

            private fun determineInternetAccess(network: Network) {
                CoroutineScope(Dispatchers.IO).launch{
                    if (InernetAvailablity.check()){
                        withContext(Dispatchers.Main){
                            valideNetworkConnections.add(network)
                            announceStatus()
                        }
                    }else{
                        withContext(Dispatchers.Main){
                            valideNetworkConnections.remove(network)
                            announceStatus()
                        }
                    }
                }
            }

        }


    object InernetAvailablity {
        fun check() : Boolean {
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress("8.8.8.8",53))
                socket.close()
                true
            } catch ( e: Exception){
                e.printStackTrace()
                false
            }
        }
    }

}