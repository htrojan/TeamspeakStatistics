import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.api.event.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Exception

class Listener(val api: TS3Api, val database: TsDatabase) : TS3Listener {

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

    override fun onClientJoin(e: ClientJoinEvent?) {
        println("Client join")
        if (e == null){
            println("Join event was null")
            return
        }
        database.registerClientJoined(e.uniqueClientIdentifier, e.clientId, e.clientTargetId)
    }

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

    override fun onServerEdit(e: ServerEditedEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onChannelEdit(e: ChannelEditedEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onChannelDescriptionChanged(e: ChannelDescriptionEditedEvent?) {
//        TODO("Not yet implemented")
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

    override fun onChannelCreate(e: ChannelCreateEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onChannelDeleted(e: ChannelDeletedEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onChannelMoved(e: ChannelMovedEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onChannelPasswordChanged(e: ChannelPasswordChangedEvent?) {
//        TODO("Not yet implemented")
    }

    override fun onPrivilegeKeyUsed(e: PrivilegeKeyUsedEvent?) {
//        TODO("Not yet implemented")
    }

}