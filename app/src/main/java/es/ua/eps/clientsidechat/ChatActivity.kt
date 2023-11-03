package es.ua.eps.clientsidechat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import es.ua.eps.clientsidechat.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding : ActivityChatBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}