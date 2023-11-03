package es.ua.eps.clientsidechat.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import es.ua.eps.clientsidechat.R
import es.ua.eps.clientsidechat.utils.Message

const val OUT_MESSAGE = 0
const val IN_MESSAGE = 1

class RecycledAdapter(val messageList: List<Message>) :
    RecyclerView.Adapter<RecycledAdapter.ViewHolder?>() {

    private var listener: (msgPosition: Int) -> Unit = {}
    private var listenerLong: (msgPosition: Int) -> Boolean = {false}

    inner class outViewHolder(v: View) : ViewHolder(v) {
        init {
            v.setOnClickListener {
                listener(adapterPosition)
            }

            v.setOnLongClickListener {
                listenerLong(adapterPosition)
            }
        }
    }

    inner class inViewHolder(v: View) : ViewHolder(v){
        init {
            v.setOnClickListener {
                listener(adapterPosition)
            }

            v.setOnLongClickListener {
                listenerLong(adapterPosition)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return messageList[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        var viewHolder : ViewHolder? = null

        when(viewType){
            OUT_MESSAGE -> {
                viewHolder = outViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.msg_item_out, parent, false)
                )
            }
            IN_MESSAGE -> {
                viewHolder = inViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.msg_item_in, parent, false)
                )
            }
        }

        return viewHolder!!
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    open class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private var author: TextView?
        private var content: TextView
        private val view = v

        fun bind(it: Message) {
            author?.text = it.authorName
            content.text = it.content

            if(it.type == IN_MESSAGE)
                author?.setTextColor(Color.parseColor(it.color))
        }

        init {
            author = view.findViewById(R.id.author)
            content = view.findViewById(R.id.content)
        }
    }

    fun setOnItemClickListener(listener: (filmPosition: Int) -> Unit) {
        this.listener = listener
    }

    fun setOnLongItemClickListener(listener: (filmPosition: Int) -> Boolean) {
        this.listenerLong = listener
    }

}