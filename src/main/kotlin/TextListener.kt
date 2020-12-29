import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.*
import com.github.theholywaffle.teamspeak3.api.exception.TS3CommandFailedException
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Exception

abstract class TsListener(public val database: TsDatabase, val queryName: String, val channelName: String) : TS3Listener{
    lateinit var api: TS3Api

        override fun onTextMessage(e: TextMessageEvent?) {
        }

        override fun onClientJoin(e: ClientJoinEvent?) {
        }

        override fun onClientLeave(e: ClientLeaveEvent?) {
        }

        override fun onServerEdit(e: ServerEditedEvent?) {
        }

        override fun onChannelEdit(e: ChannelEditedEvent?) {
        }

        override fun onChannelDescriptionChanged(e: ChannelDescriptionEditedEvent?) {
        }

        override fun onClientMoved(e: ClientMovedEvent?) {
        }

        override fun onChannelCreate(e: ChannelCreateEvent?) {
        }

        override fun onChannelDeleted(e: ChannelDeletedEvent?) {
        }

        override fun onChannelMoved(e: ChannelMovedEvent?) {
        }

        override fun onChannelPasswordChanged(e: ChannelPasswordChangedEvent?) {
        }

        override fun onPrivilegeKeyUsed(e: PrivilegeKeyUsedEvent?) {
        }

        fun init(){
            val ownId = api.whoAmI().id
            val channel = api.getChannelByNameExact(channelName, false)
            val channelId : Int = channel?.id
                ?: api.createChannel(channelName, mapOf(
                    ChannelProperty.CHANNEL_ORDER to "0",
                ))
            try {
                api.moveClient(ownId, channelId)
            }catch (e: Exception){
                println("Moving of client failed")
            }
            try {
                api.setNickname(queryName)
            } catch (e: TS3CommandFailedException){
                println(e.message)
            }
            println("Query ${queryName} spawned!")
        }
}
class LeaveListener(database: TsDatabase, channelName: String): TsListener(database, "JamesBond", channelName) {

    override fun onClientLeave(e: ClientLeaveEvent?) {
        println("Client Leave")
        if (e == null){
            println("Leave event was null")
            return
        }

        try{
            database.registerClientLeft(e.clientId)
        }catch (e: Exception){
            println(e.message)
        }
    }
}
class JoinListener(database: TsDatabase, channelName: String) : TsListener(database, "007", channelName) {
    override fun onClientJoin(e: ClientJoinEvent?) {
        println("Client join")
        if (e == null){
            println("Join event was null")
            return
        }
        database.registerClientJoined(e.uniqueClientIdentifier, e.clientId, e.clientTargetId)
    }
}

class TextListener(database: TsDatabase, channelName: String, queryName: String) : TsListener(database, queryName, channelName) {

    override fun onTextMessage(e: TextMessageEvent?) {
        if (e == null){
            println("Text message event was null")
            return
        }

        when (e.message){
            "!register" -> {
                database.registerUser(e.invokerUniqueId, e.invokerId)
                api.sendChannelMessage("Erfolgreich registriert!")
            }
            "!unregister" -> {
                database.unregisterUser(e.invokerUniqueId, e.invokerId)
                api.sendChannelMessage("Erfolgreich abgemeldet!")
            }
        }
    }

    override fun onClientMoved(e: ClientMovedEvent?) {
        if (e == null){
            println("ClientMoved event was null")
            return
        }
        try {
            database.registerClientMoved(e.invokerUniqueId, e.clientId, e.targetChannelId)
        } catch (e: Exception) {
            println(e.message)
        }
    }
}