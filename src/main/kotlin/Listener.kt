import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.api.event.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Exception
import java.time.LocalDateTime

class Listener(val api: TS3Api, val database: TsDatabase) : TS3Listener {

    override fun onTextMessage(e: TextMessageEvent?) {
        if (e == null){
            println("Text message event was null")
            return
        }
        val timestamp = LocalDateTime.now()
        val message = e.message
        if (message == "!register"){
            try {
                transaction {
                    val id = database.registerUser(e.invokerUniqueId, timestamp = timestamp)
                    if (id != null)
                        database.registerEvent(
                            EventType.UserRegistered,
                            invoker = database.uniqueUserToEntityId(e.invokerUniqueId),
                            null,
                            clientId = e.invokerId,
                            timestamp = timestamp
                        )
                }
                api.sendChannelMessage("Erfolgreich registriert!")
            }catch (e: Exception){
                println(e.message)
            }
        } else if (message == "!unregister"){
            try {
                transaction {
                    database.registerEvent(
                        EventType.UserUnregistered,
                        invoker = database.uniqueUserToEntityId(e.invokerUniqueId),
                        null,
                        clientId = e.invokerId,
                        timestamp = timestamp
                    )
                    database.unregisterUser(e.invokerUniqueId, timestamp = timestamp)
                }
                api.sendChannelMessage("Erfolgreich abgemeldet!")
            }catch (e: Exception){
                println(e.message)

            }
        }
    }

    override fun onClientJoin(e: ClientJoinEvent?) {
        println("Join was detected")
        if (e == null){
            println("Join event was null")
            return
        }
        val clientUnique = e.uniqueClientIdentifier
        val clientId = e.clientId
        val channelId = e.clientTargetId
        database.registerEvent(EventType.ClientJoined, receiver = database.uniqueUserToEntityId(clientUnique), clientId=clientId, channelId=channelId)

    }

    override fun onClientLeave(e: ClientLeaveEvent?) {
        println("Leave was detected")
        if (e == null){
            println("Leave event was null")
            return
        }
        try{
            val clientId = e.clientId
            val userId = database.lastUsedClientId(clientId)
            database.registerEvent(EventType.ClientLeft, receiver = database.userIdToEntityId(userId), clientId=clientId)
            println("Event was tried to register")
        }catch (e: Exception){
            println("Client info could not be retrieved")
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
        println("Client moved!")
        if (e == null){
            println("ClientMoved event was null")
            return
        }
        try {
            val clientId = database.lastUsedClientId(e.clientId)
            val invokerId = e.invokerUniqueId
            val channelId = e.targetChannelId
            println("InvokerUnique = ${invokerId}, ClientUnique = ${clientId}, NewChannelId = ${channelId}")
            transaction {
                database.registerEvent(
                    EventType.ClientMoved,
                    invoker = database.uniqueUserToEntityId(invokerId),
                    receiver = database.userIdToEntityId(clientId),
                    channelId = channelId
                )
            }
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