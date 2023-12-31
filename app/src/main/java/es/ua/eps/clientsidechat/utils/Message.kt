package es.ua.eps.clientsidechat.utils

import android.graphics.drawable.Drawable
import es.ua.eps.clientsidechat.adapter.OUT_MESSAGE
import java.util.Date

class Message {

    var authorName: String? = null
    var date: Date? = null
    var type: Int = OUT_MESSAGE
    var color: String = "#7F92FB"
    var content: String? = null
    var image : Drawable? = null
    var hasImage : Boolean = false

}