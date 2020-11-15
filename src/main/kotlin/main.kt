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

/**
 * @return the UserId created in the database
 */
fun getOrCreateUser(uniqueUserId: String){
    return transaction {
        Users.insertOrIgnore{
            it[uniqueId] = uniqueUserId
            it[hasAgreed] = false
        }
    }
}

fun registerUser(uniqueUserId: String, timestamp: LocalDateTime = LocalDateTime.now()): EntityID<Int>? {
    return transaction {
        addLogger(StdOutSqlLogger)
            getOrCreateUser(uniqueUserId)
            val userQuery = Users.slice(Users.id).select{(Users.uniqueId eq uniqueUserId) and (Users.hasAgreed eq false)}
            val userId = userQuery.map { it[Users.id] }.firstOrNull()
        if (userId != null){
            UserRegistrations.insert() {
                it[action] = 0
                it[UserRegistrations.timestamp] = timestamp
                it[user] =userId
            }
            Users.update({Users.id eq userId}) { it[hasAgreed] = true }
        }
        userId
    }
}

fun unregisterUser(uniqueUserId: String, timestamp: LocalDateTime = LocalDateTime.now()) {
    transaction {
        addLogger(StdOutSqlLogger)
        getOrCreateUser(uniqueUserId)
        val userQuery = Users.slice(Users.id).select{(Users.uniqueId eq uniqueUserId) and (Users.hasAgreed eq true)}

        UserRegistrations.insert() {
            it[action] = 0
            it[UserRegistrations.timestamp] = timestamp
            it[user] = userQuery.map { it[Users.id] }.first()
        }
        Users.update({Users.uniqueId eq uniqueUserId }) { it[Users.hasAgreed] = true }
    }
}
fun lastUsedClientId(userId: Int): Int? {
    return transaction {
        addLogger(StdOutSqlLogger)
        RecordedEvents.slice(RecordedEvents.invokerId)
            .select { (RecordedEvents.eventType eq 1) and (RecordedEvents.clientId eq userId) }
            .orderBy(RecordedEvents.timestamp, SortOrder.DESC).limit(1)
            .map { it[RecordedEvents.invokerId] }.firstOrNull()?.value
    }
}


private fun uniqueUserToEntityId(uniqueUserId: String?, agreedCondition: Boolean =true): EntityID<Int>? {
    return if (uniqueUserId != null && uniqueUserId != "") Users.select{(Users.uniqueId eq uniqueUserId) and (Users.hasAgreed eq true)}.
     map { it[Users.id] }.firstOrNull() else null
}

private fun userIdToEntityId(userId: Int?, agreedCondition: Boolean=true): EntityID<Int>? {
    return if (userId != null) Users.select{(Users.id eq userId) and (Users.hasAgreed eq true)}.
    map{it[Users.id]}.firstOrNull() else null
}

private fun registerEvent(eventType: Int, invoker: EntityID<Int>?=null, receiver: EntityID<Int>?=null, channelId: Int?=null, clientId: Int?=null, timestamp: LocalDateTime = LocalDateTime.now()) {
    if (invoker != null || receiver != null){
        RecordedEvents.insert {
            it[RecordedEvents.clientId] = clientId
            it[RecordedEvents.channelId] = channelId
            it[RecordedEvents.targetId] = receiver
            it[RecordedEvents.invokerId] = invoker
            it[RecordedEvents.eventType] = eventType
            it[RecordedEvents.timestamp] = timestamp
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
                transaction {
                    val id = registerUser(e.invokerUniqueId, timestamp=timestamp)
                    if (id != null)
                        registerEvent(1, invoker=uniqueUserToEntityId(e.invokerUniqueId), null, clientId= e.invokerId, timestamp = timestamp)
                }
                api.sendChannelMessage("Erfolgreich registriert!")
            }catch (e: Exception){
                println(e.message)
            }
        } else if (message == "!unregister"){
            try {
                transaction {
                    registerEvent(2, invoker=uniqueUserToEntityId(e.invokerUniqueId), null, clientId= e.invokerId, timestamp = timestamp)
                    unregisterUser(e.invokerUniqueId, timestamp=timestamp)
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
        registerEvent(1, receiver = uniqueUserToEntityId(clientUnique), clientId=clientId, channelId=channelId)

    }

    override fun onClientLeave(e: ClientLeaveEvent?) {
        println("Leave was detected")
        if (e == null){
            println("Leave event was null")
            return
        }
        try{
            val clientId = e.clientId
            val userId = lastUsedClientId(clientId)
            registerEvent(2, receiver = userIdToEntityId(userId), clientId=clientId)
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
            registerEvent(3,invoker = uniqueUserToEntityId(invokerId), receiver =  userIdToEntityId(clientId), channelId=channelId)
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