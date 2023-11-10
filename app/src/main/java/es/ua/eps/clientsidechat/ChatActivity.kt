package es.ua.eps.clientsidechat

import android.R.attr.data
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.gson.Gson
import es.ua.eps.clientsidechat.adapter.IN_MESSAGE
import es.ua.eps.clientsidechat.adapter.OUT_MESSAGE
import es.ua.eps.clientsidechat.databinding.ActivityChatBinding
import es.ua.eps.clientsidechat.fragment.MessageListFragment
import es.ua.eps.clientsidechat.utils.AESHelper
import es.ua.eps.clientsidechat.utils.ClientKey
import es.ua.eps.clientsidechat.utils.Message
import es.ua.eps.clientsidechat.utils.MessageChatData
import es.ua.eps.clientsidechat.utils.RSAHelper
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


// Constantes globales para poder transferir datos entre el MainActivity y el ChatActivity.
const val CLIENT_NAME = "client_name"
const val PORT_NUMBER = "port_number"
const val SERVER_ADDRESS = "server_address"
const val REQUEST_MEDIA_FILE = 1

class ChatActivity : AppCompatActivity() {

    private lateinit var binding : ActivityChatBinding

    private var mediaPlayer : MediaPlayer? = null   // Clase que nos permite lanzar eventos de sonido.

    private var clientName : String = ""            // Nombre del cliente.
    private var clientThread : ClientThread? = null // Hilo del cliente.

    private val rsaHelper = RSAHelper()             // Generador de claves RSA. Puede encriptar y desencriptar.
    private val aesHelper = AESHelper()             // Desencriptador / Encriptador utilizando claves simétricas AES.

    private var bitmapToSend : Bitmap? = null

    private val startForResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            onActivityResult(REQUEST_MEDIA_FILE, result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtenemos los datos enviados desde el MainActivity (dirección, puerto, y nombre de usuario)
        clientName = intent.getStringExtra(CLIENT_NAME)?: ""
        val serverAddress = intent.getStringExtra(SERVER_ADDRESS) ?: ""
        val serverPort = intent.getIntExtra(PORT_NUMBER, 0)

        generateBindings()

        // Generamos clave pública y privada RSA.
        rsaHelper.generateKeyPair()

        // Creamos el hilo del cliente y lo lanzamos.
        clientThread = ClientThread(serverAddress, serverPort)
        clientThread?.start()
    }

    private fun generateBindings(){

        binding.textInput.doOnTextChanged { text, start, before, count ->
            run {
                if (text!!.isNotBlank()) {
                    binding.btnSend.visibility = View.VISIBLE
                    binding.btnSend.isEnabled = true
                    binding.btnMedia.visibility = View.GONE
                    binding.btnMedia.isEnabled = false
                }else{
                    binding.btnSend.visibility = View.GONE
                    binding.btnSend.isEnabled = false
                    binding.btnMedia.visibility = View.VISIBLE
                    binding.btnMedia.isEnabled = true
                }
            }
        }

        // Generamos el binding del botón de enviar mensaje.
        binding.btnSend.setOnClickListener {
            if(binding.textInput.text.isNotBlank() && clientThread != null){
                val formatText = binding.textInput.text.toString().replace(Regex(" {2,}"), " ").trim()
                addMessageMainThread(createMessage(formatText, OUT_MESSAGE, clientName))
                clientThread?.sendMsg(formatText)
            }
        }

        // Generamos el binding del botón de enviar imágenes.
        binding.btnMedia.setOnClickListener {
            val mediaFileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            if (intent.resolveActivity(packageManager) != null) {
                if(Build.VERSION.SDK_INT >= 30) {
                    startForResult.launch(mediaFileIntent)
                }
                else
                    @Suppress("DEPRECATION")
                    startActivityForResult(mediaFileIntent, REQUEST_MEDIA_FILE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_FILE && resultCode == RESULT_OK){
            val filePath: Uri? = data?.data

            if(filePath != null) {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(filePath)
                    bitmapToSend = BitmapFactory.decodeStream(inputStream)
                    Log.i(PACKAGE_NAME, bitmapToSend.toString())
                    if(bitmapToSend != null) {
                        val drawable = BitmapDrawable(resources, bitmapToSend)
                        addMessageMainThread(createMessage("", OUT_MESSAGE, clientName, drawable))
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
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
                    if(inputStream.available() > 0){                                                // Si el flujo de datos de entrada no está vacío, lo leemos.
                        val receivedMsg = inputStream.readUTF()                                     // Leemos los datos de entrada.
                        val decryptedMsg = aesHelper.decrypt(receivedMsg, aesKey!!)                 // Desencriptamos el mensaje utilizando la clave AES.
                        val parsedMsg = Gson().fromJson(decryptedMsg, Message::class.java)          // Los transformamos de JSON a un objeto Message de forma automática.
                        parsedMsg.type = IN_MESSAGE                                                 // Indicamos que el mensaje es de entrada.

                        if(parsedMsg.hasImage){
                            val len = inputStream.readInt()
                            val data = ByteArray(len)
                            inputStream.readFully(data, 0, data.size)
                            parsedMsg.image = parseImageByteArray(data)                             // Obtenemos el Drawable a mostrar a partir del bitmap
                        }

                        Log.i(PACKAGE_NAME, "Message received: $decryptedMsg")

                        startAudio(R.raw.pop_up)
                        addMessageMainThread(parsedMsg)                                             // Añadimos el mensaje recibido a la pantalla.
                    }

                    if(msgToSend.isNotBlank() || bitmapToSend != null){                             // Comprobamos si el cliente ha generado un mensaje que se deba enviar al servidor.
                        val msg = createMessage(msgToSend, IN_MESSAGE, clientName)                  // Generamos el objeto de tipo Message.
                        if(bitmapToSend != null) msg.hasImage = true                                // Indicamos si el mensaje lleva una imagen adjunta
                        val rawMsg = Gson().toJson(msg)                                             // Lo transformamos a una cadena JSON.
                        val encryptedMsg = aesHelper.encrypt(rawMsg, aesKey!!)                      // Encriptamos el mensaje utilizando la clave AES.
                        outputStream.writeUTF(encryptedMsg)                                         // Lo escribimos por el flujo de salida de datos.
                        outputStream.flush()
                        msgToSend = ""                                                              // Reiniciamos el valor del mensaje para evitar que se vuelva a enviar.

                        if(msg.hasImage) {
                            val stream = ByteArrayOutputStream()
                            bitmapToSend!!.compress(Bitmap.CompressFormat.PNG, 0, stream)    // Comprimimos la imagen para enviar sus bytes por socket.
                            val array = stream.toByteArray()
                            outputStream.writeInt(array.size)
                            outputStream.write(array,0,array.size)                              // Lo escribimos por el flujo de salida de datos.
                            outputStream.flush()
                            bitmapToSend = null                                                     // Reiniciamos el valor del bitmap
                        }
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

    private fun createMessage(content : String, type : Int, name : String, image : Drawable) : Message{
        val newMsg = createMessage(content, type, name)
        newMsg.image = image
        return newMsg
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

    private fun parseImageByteArray(byteArray : ByteArray?) : Drawable?{
        if(byteArray != null) {
            val bitmapReceived = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            return BitmapDrawable(resources, bitmapReceived)
        }
        return null
    }
}