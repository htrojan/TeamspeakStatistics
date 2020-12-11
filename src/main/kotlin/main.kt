import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.*
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Exception
import java.time.LocalDateTime

val teamspeak_host = Key("teamspeak.host", stringType)
val teamspeak_user = Key("teamspeak.user", stringType)
val teamspeak_password = Key("teamspeak.password", stringType)

val database_host = Key("database.host", stringType)
val database_port = Key("database.port", stringType)
val database_user = Key("database.user", stringType)
val database_password = Key("database.password", stringType)
val database_name = Key("database.name", stringType)

val channel_name = Key("channel.name", stringType)
val channel_description = Key("channel.description", stringType)

val database: TsDatabase = TsDatabase()

fun connectApi(host: String, user: String, password: String): TS3Api {
    val tsconfig: TS3Config = TS3Config()
    tsconfig.setHost(host)
    val query: TS3Query = TS3Query(tsconfig)
    query.connect()

    val api = query.api
    api.login(user, password)
    api.selectVirtualServerById(1)

    return api
}



fun main() {
    print("Starting")
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database_stage.properties") overriding
            ConfigurationProperties.fromResource("query.properties")

    database.connect(config)

    val api = connectApi(config[teamspeak_host], config[teamspeak_user], config[teamspeak_password])
    val ownId = api.whoAmI().id
    val channel = api.getChannelByNameExact(config[channel_name], false)
    val channelId : Int = channel?.id
        ?: api.createChannel(config[channel_name], mapOf(ChannelProperty.CHANNEL_ORDER to "0",
            ChannelProperty.CHANNEL_DESCRIPTION to config[channel_description]))
    try {
        api.moveClient(ownId, channelId)
    }catch (e: Exception){
        println("Movint of client failed")
    }

    api.addTS3Listeners(Listener(api))
    api.registerAllEvents()
}

class Listener(val api: TS3Api) : TS3Listener {

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
                    val id = database.registerUser(e.invokerUniqueId, timestamp=timestamp)
                    if (id != null)
                        database.registerEvent(1, invoker=database.uniqueUserToEntityId(e.invokerUniqueId), null, clientId= e.invokerId, timestamp = timestamp)
                }
                api.sendChannelMessage("Erfolgreich registriert!")
            }catch (e: Exception){
                println(e.message)
            }
        } else if (message == "!unregister"){
            try {
                transaction {
                    database.registerEvent(2, invoker=database.uniqueUserToEntityId(e.invokerUniqueId), null, clientId= e.invokerId, timestamp = timestamp)
                    database.unregisterUser(e.invokerUniqueId, timestamp=timestamp)
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
        database.registerEvent(1, receiver = database.uniqueUserToEntityId(clientUnique), clientId=clientId, channelId=channelId)

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
            database.registerEvent(2, receiver = database.userIdToEntityId(userId), clientId=clientId)
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
            database.registerEvent(3,invoker = database.uniqueUserToEntityId(invokerId), receiver =  database.userIdToEntityId(clientId), channelId=channelId)
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