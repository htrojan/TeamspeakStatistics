import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.*
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.Key
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
        SchemaUtils.create(Users, UserRegistrations, RecordedEvents)
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
    api.registerEvent(TS3EventType.SERVER)
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

fun registerUser(uniqueUserId: String) {
    transaction{
    val user = getOrCreateUser(uniqueUserId)
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

fun registerEvent(invokerId: String?, receiverId: String?, objId: Int?, eventType: Int) {
    transaction {
        val invoker = if (invokerId != null) User[invokerId] else null
        val receiver = if (receiverId != null) User[receiverId] else null

        val invokerAgree = (invoker != null && invoker.hasAgreed)
        val receiverAgree = (receiver != null && receiver.hasAgreed)

        if (invokerAgree || receiverAgree){
            RecordedEvent.new {
                this.invoker = if (invokerAgree) invoker else null
                this.target = if (receiverAgree) invoker else null
                this.obj1 = objId
                this.eventType = eventType
                this.timestamp = LocalDateTime.now()
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

    fun lastJoinedEvent(userId: Int): String? {
        return transaction {
             RecordedEvents.slice(RecordedEvents.invoker)
                 .select { (RecordedEvents.eventType eq 1) and (RecordedEvents.obj1 eq userId) }
                .orderBy(RecordedEvents.timestamp, SortOrder.DESC).limit(1)
                 .map { it[RecordedEvents.invoker] }.firstOrNull()?.value
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
        registerEvent(clientUnique, null, clientId, 1)

    }

    override fun onClientLeave(e: ClientLeaveEvent?) {
        println("Leave was detected")
        if (e == null){
            println("Leave event was null")
            return
        }
        try{
            val clientId = e.clientId
            val uniqueId = lastJoinedEvent(clientId)
            registerEvent(uniqueId, null, e.clientId, 2)
            println("Event was tried to register")
        }catch (e: Exception){
            println("Client info could not be retrieved")
            println(e.message)
        }
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