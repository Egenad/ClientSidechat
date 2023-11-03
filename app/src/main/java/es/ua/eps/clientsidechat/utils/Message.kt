package es.ua.eps.clientsidechat.utils

import es.ua.eps.clientsidechat.adapter.OUT_MESSAGE
import java.util.Date
import java.util.UUID

class Message {

    var messageId: UUID? = null
    var authorName: String? = null
    var authorIP: String? = null
    var date: Date? = null
    var type: Int = OUT_MESSAGE
    var color: String = "#7F92FB"
    var content: String? = null

}