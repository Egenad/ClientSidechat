package es.ua.eps.clientsidechat

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import es.ua.eps.clientsidechat.adapter.IN_MESSAGE
import es.ua.eps.clientsidechat.adapter.OUT_MESSAGE
import es.ua.eps.clientsidechat.databinding.ActivityChatBinding
import es.ua.eps.clientsidechat.fragment.MessageListFragment
import es.ua.eps.clientsidechat.utils.ClientKey
import es.ua.eps.clientsidechat.utils.Message
import es.ua.eps.clientsidechat.utils.MessageChatData
import es.ua.eps.clientsidechat.utils.RSAHelper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.Base64
import java.util.Date
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


// Constantes globales para poder transferir datos entre el MainActivity y el ChatActivity.
const val CLIENT_NAME = "client_name"
const val PORT_NUMBER = "port_number"
const val SERVER_ADDRESS = "server_address"

class ChatActivity : AppCompatActivity() {

    private lateinit var binding : ActivityChatBinding

    private var mediaPlayer : MediaPlayer? = null   // Clase que nos permite lanzar eventos de sonido.

    private var clientName : String = ""            // Nombre del cliente.
    private var clientThread : ClientThread? = null // Hilo del cliente.

    private val rsaHelper = RSAHelper()             // Generador de claves RSA. Puede encriptar y desencriptar.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtenemos los datos enviados desde el MainActivity (dirección, puerto, y nombre de usuario)
        clientName = intent.getStringExtra(CLIENT_NAME)?: ""
        val serverAddress = intent.getStringExtra(SERVER_ADDRESS) ?: ""
        val serverPort = intent.getIntExtra(PORT_NUMBER, 0)

        // Generamos el binding del botón de enviar mensaje.
        binding.btnSend.setOnClickListener {
            if(binding.textInput.text.isNotBlank() && clientThread != null){
                addMessageMainThread(createMessage(binding.textInput.text.toString(), OUT_MESSAGE, clientName))
                clientThread?.sendMsg(binding.textInput.text.toString())
            }
        }

        // Generamos clave pública y privada RSA.
        rsaHelper.generateKeyPair()

        // Creamos el hilo del cliente y lo lanzamos.
        clientThread = ClientThread(serverAddress, serverPort)
        clientThread?.start()
    }

    inner class ClientThread(address : String, port : Int) : Thread() {

        val ipAddress = address     // Dirección IP del servidor al que conectarse.
        val portNumber = port       // Número de puerto a utilizar.

        var stopThread = false      // Indica si debemos pausar el bucle principal del hilo.
        var msgToSend = ""          // El mensaje que el cliente desea enviar al resto de participantes.

        var socket : Socket? = null // El socket del usuario.

        var aesKey : SecretKey? = null

        override fun run() {
            super.run()

            var inputStream : DataInputStream? = null
            var outputStream : DataOutputStream? = null

            try {

                // Creamos el socket del cliente con la dirección y el puerto.
                socket = Socket(ipAddress, portNumber)

                // Obtenemos el flujo de datos de entrada y salida.
                inputStream = DataInputStream(socket?.getInputStream())
                outputStream = DataOutputStream(socket?.getOutputStream())

                // Enviamos el nombre de usuario y la clave pública RSA al Server en una primera instancia.
                val clientKey = ClientKey()
                clientKey.clientName = clientName
                val encodedKey = android.util.Base64.encodeToString(rsaHelper.generateKeyPair().public.encoded, android.util.Base64.DEFAULT)
                clientKey.publicKey = encodedKey

                val jsonString = Gson().toJson(clientKey)

                outputStream.writeUTF(jsonString)
                outputStream.flush()

                // Leemos la clave AES encriptada y la desencriptamos utilizando la clave RSA privada
                val encryptedAESKey = inputStream.readUTF()
                val desencryptedAESKey = rsaHelper.decrypt(encryptedAESKey, rsaHelper.generateKeyPair().private)

                // Transformamos la clave AES de String a SecretKey
                val decodedKey: ByteArray = android.util.Base64.decode(desencryptedAESKey, android.util.Base64.DEFAULT)
                aesKey = SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")

                Log.i(PACKAGE_NAME, "AES Key: $aesKey")

                while(!stopThread){
                    if(inputStream.available() > 0){ // Si el flujo de datos de entrada no está vacío, lo leemos.
                        val receivedMsg = inputStream.readUTF() // Leemos los datos de entrada.
                        val parsedMsg = Gson().fromJson(receivedMsg, Message::class.java) // Los transformamos de JSON a un objeto Message de forma automática.
                        parsedMsg.type = IN_MESSAGE // Indicamos que el mensaje es de entrada.
                        Log.i(PACKAGE_NAME, "Message received: $receivedMsg")
                        startAudio(R.raw.pop_up)
                        addMessageMainThread(parsedMsg) // Añadimos el mensaje recibido a la pantalla.
                    }

                    if(msgToSend.isNotBlank()){ // Comprobamos si el cliente ha generado un mensaje que se deba enviar al servidor.
                        val msg = createMessage(msgToSend, IN_MESSAGE, clientName) // Generamos el objeto de tipo Message.
                        val jsonString = Gson().toJson(msg) // Lo transformamos a una cadena JSON.
                        outputStream.writeUTF(jsonString) // Lo escribimos por el flujo de salida de datos.
                        outputStream.flush()
                        msgToSend = "" // Reiniciamos el valor del mensaje para evitar que se vuelva a enviar.
                    }
                }
            }catch (error : Exception){
                Log.e(PACKAGE_NAME, error.stackTraceToString())
            }finally {
                if (socket != null) { // Cerramos el socket para terminar la comunicación
                    try {
                        socket?.close();
                    } catch (error: IOException) {
                        Log.e(PACKAGE_NAME, error.stackTraceToString())
                    }
                }
            }

            // Cerramos los flujos de entrada y salida de datos.

            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    Log.e(PACKAGE_NAME, e.stackTraceToString())
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    Log.e(PACKAGE_NAME, e.stackTraceToString())
                }
            }

            // Finalizamos la actividad para volver al MainActivity.
            this@ChatActivity.runOnUiThread {
                finish()
            }
        }

        fun sendMsg(msg: String) {
            msgToSend = msg
        }
    }

    private fun createMessage(content : String, type : Int, name : String) : Message{
        val msg = Message()
        msg.content = content
        msg.type = type

        when(type){
            OUT_MESSAGE -> startAudio(R.raw.send_message)
            IN_MESSAGE -> startAudio(R.raw.pop_up)
        }

        msg.date = Date()
        msg.authorName = name
        return msg
    }

    private fun addMessageMainThread(message : Message){
        binding.listFragment.post{
            MessageChatData.messages.add(message)
            val listFragmentStatic = supportFragmentManager.findFragmentById(R.id.list_fragment) as? MessageListFragment
            listFragmentStatic?.notifyItemInserted()
        }
        binding.textInput.post{
            binding.textInput.setText("")
        }
    }

    private fun startAudio(audio : Int){
        mediaPlayer = MediaPlayer.create(this, audio)
        mediaPlayer?.start()
    }
}