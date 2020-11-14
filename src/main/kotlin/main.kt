import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.TextMessageTargetMode
import com.github.theholywaffle.teamspeak3.api.event.*
import com.github.theholywaffle.teamspeak3.api.exception.TS3CommandFailedException
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.lang.Exception
import java.time.LocalDateTime
import javax.management.Query.eq
import kotlin.concurrent.thread

val teamspeak_host = Key("teamspeak.host", stringType)
val teamspeak_user = Key("teamspeak.user", stringType)
val teamspeak_password = Key("teamspeak.password", stringType)

val database_host = Key("database.host", stringType)
val database_port = Key("database.port", stringType)
val database_user = Key("database.user", stringType)
val database_password = Key("database.password", stringType)
val database_name = Key("database.name", stringType)


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

fun initDatabase() {
    val dbconfig = ConfigurationProperties.fromResource("database.properties")
    Database.connect(
        "jdbc:postgresql://${dbconfig[database_host]}:${dbconfig[database_port]}/${dbconfig[database_name]}",
        user = dbconfig[database_user], password = dbconfig[database_password])
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Users, UserRegistrations)
        println("${User.all()}")
    }
}

fun main() {
    print("Starting")
    initDatabase()
    val tsconfig = ConfigurationProperties.fromResource("teamspeak.properties")
    val api = connectApi(tsconfig[teamspeak_host], tsconfig[teamspeak_user], tsconfig[teamspeak_password])

    val channelId = api.createChannel("Orakelkanal - Live", mapOf(ChannelProperty.CHANNEL_ORDER to "0",
    ChannelProperty.CHANNEL_DESCRIPTION to "Werde erleuchtet mit durchdringendem Wissen"))

    api.addTS3Listeners(Listener(api))
    api.registerEvent(TS3EventType.TEXT_CHANNEL, channelId)
}

fun getOrCreateUser(uniqueUserId: String): User {
    var user = transaction {
        addLogger(StdOutSqlLogger)
        User.findById(uniqueUserId)
    }
    println("user = $user")
    if (user == null) {
        print("Registering user in database")
        user = transaction {
            User.new(uniqueUserId) {
                hasAgreed = true
            }
        }
    }
    return user
}

fun registerUser(uniqueUserId: String) {
    val user = getOrCreateUser(uniqueUserId)
    transaction {
        user.hasAgreed = true
        UserRegistration.new() {
            this.timestamp = LocalDateTime.now()
            this.action = 1
            this.user = user
        }
    }
}

fun unregisterUser(invokerUniqueId: String) {
    val user = getOrCreateUser(invokerUniqueId)
    transaction {
        user.hasAgreed = false
        UserRegistration.new() {
            this.timestamp = LocalDateTime.now()
            this.action = 0
            this.user = user
        }
    }
}
class Listener(val api: TS3Api) : TS3Listener {

    override fun onTextMessage(e: TextMessageEvent?) {
        if (e == null){
            println("Text message event was null")
            return
        }
        val message = e.message
        if (message == "!register"){
            try {
                registerUser(e.invokerUniqueId)
                api.sendChannelMessage("Erfolgreich registriert!")
            }catch (e: Exception){
                println(e.message)
            }
        } else if (message == "!unregister"){
            try {
                unregisterUser(e.invokerUniqueId)
                api.sendChannelMessage("Erfolgreich abgemeldet!")
            }catch (e: Exception){
                println(e.message)

            }
        }
    }


    override fun onClientJoin(e: ClientJoinEvent?) {
        TODO("Not yet implemented")
    }

    override fun onClientLeave(e: ClientLeaveEvent?) {
        TODO("Not yet implemented")
    }

    override fun onServerEdit(e: ServerEditedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onChannelEdit(e: ChannelEditedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onChannelDescriptionChanged(e: ChannelDescriptionEditedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onClientMoved(e: ClientMovedEvent?) {
        print("Client moved!")
    }

    override fun onChannelCreate(e: ChannelCreateEvent?) {
        TODO("Not yet implemented")
    }

    override fun onChannelDeleted(e: ChannelDeletedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onChannelMoved(e: ChannelMovedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onChannelPasswordChanged(e: ChannelPasswordChangedEvent?) {
        TODO("Not yet implemented")
    }

    override fun onPrivilegeKeyUsed(e: PrivilegeKeyUsedEvent?) {
        TODO("Not yet implemented")
    }

}