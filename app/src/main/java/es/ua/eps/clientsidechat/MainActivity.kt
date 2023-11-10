package es.ua.eps.clientsidechat

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import es.ua.eps.clientsidechat.databinding.ActivityMainBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.Socket

const val PACKAGE_NAME = "es.ua.eps.client"
const val PERMISSION_CODE = 101

@DelicateCoroutinesApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
    }

    private fun checkPermissions(){

        val permsArray = arrayOf(
            android.Manifest.permission.INTERNET
        )

        if(hasPermissions(permsArray))
            startClient()
        else
            askPermissions(permsArray)
    }

    private fun hasPermissions(perms : Array<String>) : Boolean{
        return perms.all {
            return ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun askPermissions(perms : Array<String>){
        ActivityCompat.requestPermissions(
            this,
            perms,
            PERMISSION_CODE)
    }

    private fun startClient(){
        binding.connectButton.setOnClickListener{
            // Verificamos que los campos estén rellenados y que la dirección IP y el puerto sean válidos

            val serverAddress = binding.serverIp.text.toString()

            if(binding.port.text.isNotBlank() && serverAddress.isNotBlank() && binding.username.text.isNotBlank()){
                if(serverAddress.matches(Regex("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\$"))){
                    if(binding.port.text.matches(Regex("\\d{2,5}"))) {
                        val clientName = binding.username.text.toString()
                        startActivity(
                            Intent(this, ChatActivity::class.java)
                                .putExtra(CLIENT_NAME, clientName)
                                .putExtra(SERVER_ADDRESS, serverAddress)
                                .putExtra(
                                    PORT_NUMBER,
                                    Integer.parseInt(binding.port.text.toString())
                                )
                        )
                    }else Toast.makeText(this, resources.getString(R.string.invalid_port), Toast.LENGTH_SHORT).show()
                }else Toast.makeText(this, resources.getString(R.string.invalid_ip), Toast.LENGTH_SHORT).show()
            }else Toast.makeText(this, resources.getString(R.string.empty_fields), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_CODE){
            val allPerms = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if(grantResults.isNotEmpty() && allPerms)
                startClient()
        }
    }

    inner class ClientSideTask(ipAddress : String, port : Int){
        private val serverAddress = ipAddress
        private val serverPort = port
        private var response = ""

        fun runCoroutine(){
            GlobalScope.launch(Dispatchers.IO){

                try{

                    val socket = Socket("10.0.2.2", serverPort)

                    val byteArrayOutputStream = ByteArrayOutputStream(1024)
                    val buffer = ByteArray(1024)

                    val inputStream = socket.getInputStream()
                    var bytesRead: Int = inputStream.read(buffer)

                    while (bytesRead != -1){
                        byteArrayOutputStream.write(buffer, 0, bytesRead);
                        response += byteArrayOutputStream.toString("UTF-8");
                        bytesRead = inputStream.read(buffer)
                    }

                    Log.i(PACKAGE_NAME, response)

                }catch(error : Exception){
                    Log.e(PACKAGE_NAME, error.stackTraceToString())
                }
            }
        }
    }
}