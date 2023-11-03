package es.ua.eps.clientsidechat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import es.ua.eps.clientsidechat.databinding.ActivityMainBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.Socket

const val PACKAGE_NAME = "es.ua.eps.client"

@DelicateCoroutinesApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectButton.setOnClickListener{
            if(binding.port.text.isNotBlank() && binding.serverIp.text.isNotBlank()){

                /*try {
                    val portNumber = Integer.parseInt(binding.port.text.toString())
                    val clientSideTask = ClientSideTask(binding.serverIp.text.toString(), portNumber)
                    clientSideTask.runCoroutine()

                }catch (error : NumberFormatException){
                    Log.e(PACKAGE_NAME, error.stackTraceToString())
                }*/
                startActivity(Intent(this, ChatActivity::class.java))
            }else Toast.makeText(this, resources.getString(R.string.empty_fields), Toast.LENGTH_SHORT).show()
        }
    }

    inner class ClientSideTask(ipAddress : String, port : Int){
        private val serverAddress = ipAddress
        private val serverPort = port
        private var response = ""

        fun runCoroutine(){
            GlobalScope.launch(Dispatchers.IO){

                try{

                    val socket = Socket(serverAddress, serverPort)

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