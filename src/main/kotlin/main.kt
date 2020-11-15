import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.*
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Exception
import java.sql.ResultSet
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
    val dbconfig = ConfigurationProperties.fromResource("database_stage.properties")
    Database.connect(
        "jdbc:postgresql://${dbconfig[database_host]}:${dbconfig[database_port]}/${dbconfig[database_name]}",
        user = dbconfig[database_user], password = dbconfig[database_password])
    transaction {
        SchemaUtils.create(Users, UserRegistrations, RecordedEvents)
    }
}

fun main() {
    print("Starting")
    initDatabase()
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database_stage.properties") overriding
            ConfigurationProperties.fromResource("query.properties")

    val api = connectApi(config[teamspeak_host], config[teamspeak_user], config[teamspeak_password])
    val channel = api.getChannelByNameExact(config[channel_name], false)
    val t = channel?.id
        ?: api.createChannel(config[channel_name], mapOf(ChannelProperty.CHANNEL_ORDER to "0",
            ChannelProperty.CHANNEL_DESCRIPTION to config[channel_description]))

    api.addTS3Listeners(Listener(api))
    api.registerAllEvents()
}

fun <T:Any> String.execAndMap(transform : (ResultSet) -> T) : List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}

fun <T : Table> T.insertOrIgnore(vararg keys: Column<*>, body: T.(InsertStatement<Number>) -> Unit) =
    InsertOrIgnore<Number>(this, keys = *keys).apply {
        body(this)
        execute(TransactionManager.current())
    }

class InsertOrIgnore<Key : Any>(
    table: Table,
    isIgnore: Boolean = false,
    private vararg val keys: Column<*>
) : InsertStatement<Key>(table, isIgnore) {
    override fun prepareSQL(transaction: Transaction): String {
        val tm = TransactionManager.current()
        val onConflict = "ON CONFLICT ${keys.joinToString { tm.identity(it) }} DO NOTHING"
        return "${super.prepareSQL(transaction)} $onConflict"
    }
}

fun getOrCreateUser(uniqueUserId: String): User {
    return transaction {
//        "insert into users (has_agreed, unique_id) VALUES (FALSE, '${uniqueUserId}') ON CONFLICT DO NOTHING".execAndMap {}
        Users.insertOrIgnore{
            it[uniqueId] = uniqueUserId
            it[hasAgreed] = false
        }
        User.findById(uniqueUserId)
    } ?: throw Exception("User not found in database after creation.")
}

fun registerUser(uniqueUserId: String, timestamp: LocalDateTime = LocalDateTime.now()) {
    transaction{
        val user = getOrCreateUser(uniqueUserId)
        if (!user.hasAgreed){
            user.hasAgreed = true
            UserRegistration.new() {
                this.timestamp = timestamp
                this.action = 1
                this.user = user
            }
        }
    }
}

fun unregisterUser(invokerUniqueId: String, timestamp: LocalDateTime = LocalDateTime.now()) {
    val user = getOrCreateUser(invokerUniqueId)
    transaction {
//        addLogger(StdOutSqlLogger)
        if (user.hasAgreed){
            user.hasAgreed = false
            UserRegistration.new() {
                this.timestamp = timestamp
                this.action = 0
                this.user = user
            }
        }
    }
}
fun lastUsedClientId(userId: Int): String? {
    return transaction {
//        addLogger(StdOutSqlLogger)
        RecordedEvents.slice(RecordedEvents.invoker)
            .select { (RecordedEvents.eventType eq 1) and (RecordedEvents.obj1 eq userId) }
            .orderBy(RecordedEvents.timestamp, SortOrder.DESC).limit(1)
            .map { it[RecordedEvents.invoker] }.firstOrNull()?.value
    }
}


fun registerEvent(eventType: Int, invokerId: String?, receiverId: String?=null, obj1: Int? = null, obj2: Int? = null, timestamp: LocalDateTime = LocalDateTime.now()) {
    transaction {
        addLogger(StdOutSqlLogger)
        val invoker = if (invokerId != null && invokerId != "") User[invokerId] else null
        val receiver = if (receiverId != null && receiverId != "") User[receiverId] else null

        val invokerAgree = (invoker != null && invoker.hasAgreed)
        val receiverAgree = (receiver != null && receiver.hasAgreed)

        if (invokerAgree || receiverAgree){
            RecordedEvent.new {
                this.invoker = if (invokerAgree) invoker else null
                this.target = if (receiverAgree) receiver else null
                this.obj1 = obj1
                this.obj2 = obj2
                this.eventType = eventType
                this.timestamp = timestamp
            }
        }

    }
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
                registerUser(e.invokerUniqueId, timestamp=timestamp)
                registerEvent(1, e.invokerUniqueId, null, obj1=e.invokerId, timestamp = timestamp)
                api.sendChannelMessage("Erfolgreich registriert!")
            }catch (e: Exception){
                println(e.message)
            }
        } else if (message == "!unregister"){
            try {
                registerEvent(2, e.invokerUniqueId, null, obj1=e.invokerId, timestamp = timestamp)
                unregisterUser(e.invokerUniqueId, timestamp=timestamp)
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
        registerEvent(1, clientUnique, null, obj1=clientId, obj2=channelId)

    }

    override fun onClientLeave(e: ClientLeaveEvent?) {
        println("Leave was detected")
        if (e == null){
            println("Leave event was null")
            return
        }
        try{
            val clientId = e.clientId
            val uniqueId = lastUsedClientId(clientId)
            registerEvent(2, uniqueId, null, obj1=clientId)
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
            val clientId = lastUsedClientId(e.clientId)
            val invokerId = e.invokerUniqueId
            val channelId = e.targetChannelId
            println("InvokerUnique = ${invokerId}, ClientUnique = ${clientId}, NewChannelId = ${channelId}")
            registerEvent(3, invokerId, clientId, obj1=channelId)
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