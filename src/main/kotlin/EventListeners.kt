import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.*
import com.github.theholywaffle.teamspeak3.api.exception.TS3CommandFailedException
import mu.KotlinLogging
import java.lang.Exception

private val logger = KotlinLogging.logger {  }
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
            logger.debug("Initializing new EventListener")
            val ownId = api.whoAmI().id
            val channel = api.getChannelByNameExact(channelName, false)
            val channelId : Int = channel?.id
                ?: api.createChannel(channelName, mapOf(
                    ChannelProperty.CHANNEL_ORDER to "0",
                ))
            try {
                logger.debug("Moving query to channel {}", channelName)
                api.moveClient(ownId, channelId)
            }catch (e: Exception){
                logger.error("Moving of query failed", e)
            }
            try {
                logger.debug("Setting name to {}", queryName)
                api.setNickname(queryName)
            } catch (e: TS3CommandFailedException){
                logger.error("Setting nickname of query failed", e)
            }
            logger.info("Initialized new EventListener \"{}\" in channel {}", queryName, channelName)
        }
}
class LeaveListener(database: TsDatabase, channelName: String): TsListener(database, "JamesBond", channelName) {

    override fun onClientLeave(event: ClientLeaveEvent?) {
        if (event == null){
            logger.error("Leave event was null")
            return
        }

        try{
            database.registerClientLeft(event.clientId)
        }catch (e: Exception){
            logger.error("Error while registering onClientLeave event for clientId {}", event.clientId, e)
        }
    }
}
class JoinListener(database: TsDatabase, channelName: String) : TsListener(database, "007", channelName) {
    override fun onClientJoin(e: ClientJoinEvent?) {
        if (e == null){
            logger.error("Join event was null")
            return
        }
        database.registerClientJoined(e.uniqueClientIdentifier, e.clientId, e.clientTargetId)
    }
}

class TextListener(database: TsDatabase, channelName: String, queryName: String) : TsListener(database, queryName, channelName) {

    override fun onTextMessage(e: TextMessageEvent?) {
        if (e == null){
            logger.error("Text event was null")
            return
        }

        logger.debug("Client {} sent channel message {}", e.invokerUniqueId, e.message)
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
            logger.error("Client move event was null")
            return
        }
        try {
            database.registerClientMoved(e.invokerUniqueId, e.clientId, e.targetChannelId)
        } catch (e: Exception) {
            logger.error("Error while registering onClientMovedEvent")
        }
    }
}