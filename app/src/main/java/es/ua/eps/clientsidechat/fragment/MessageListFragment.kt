package es.ua.eps.clientsidechat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import es.ua.eps.clientsidechat.adapter.RecycledAdapter
import es.ua.eps.clientsidechat.databinding.MessageListRecycledBinding
import es.ua.eps.clientsidechat.utils.MessageChatData

class MessageListFragment : Fragment() {

    private lateinit var binding : MessageListRecycledBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return createListView(container)
    }

    private fun createListView(container: ViewGroup?) : View{

        binding = MessageListRecycledBinding.inflate(layoutInflater, container, false)

        binding.list.layoutManager = LinearLayoutManager(activity as AppCompatActivity?)
        binding.list.itemAnimator = DefaultItemAnimator()
        val recyclerAdapter = RecycledAdapter(MessageChatData.messages)
        binding.list.adapter = recyclerAdapter

        return binding.list
    }

    fun notifyItemInserted(){
        binding.list.adapter?.notifyItemInserted(MessageChatData.messages.size - 1)
        binding.list.smoothScrollToPosition(MessageChatData.messages.size - 1)
    }

    fun notifyItemModified(position: Int){
        binding.list.adapter?.notifyItemChanged(position)
    }

}