import com.natpryce.konfig.Configuration
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneId

private val logger = KotlinLogging.logger {  }
class TsDatabase {
    val TIMEZONE: ZoneId = ZoneId.of("Europe/Berlin")

    fun connect(dbconfig: Configuration) {
        Database.connect(
            "jdbc:postgresql://${dbconfig[database_host]}:${dbconfig[database_port]}/${dbconfig[database_name]}",
            user = dbconfig[database_user], password = dbconfig[database_password])
        transaction {
            SchemaUtils.create(Users, UserRegistrations, RecordedEvents, MetaEvents)
        }
    }

    fun registerClientJoined(clientUId: String, clientId: Int, channelId: Int?) {
        transaction {
            registerEvent(EventType.ClientJoined, target = database.getUserId(clientUId), clientId=clientId, channelId=channelId)
        }
    }

    fun registerClientLeft(clientId: Int){
        transaction {
            val userId = database.lastUserOfClientId(clientId)
            logger.debug("Registering client left event with userId: {}, clientId: {}", userId, clientId)
            registerEvent(EventType.ClientLeft, target = userId, clientId=clientId)
        }
    }

    fun registerClientMoved(invokerUId: String, targetClientId: Int, newChannelId: Int){
        val target = lastUserOfClientId(targetClientId)
        transaction {
            val inv = database.getUserId(invokerUId)
            logger.debug("Registering client moved event with invokerUID: {}, invokerId: {}, target: {}, channelId: {}",
                invokerUId, inv, target, newChannelId)
            database.registerEvent(
                EventType.ClientMoved,
                invoker = inv,
                target = target,
                channelId = newChannelId
            )
        }
    }

    fun registerEvent(eventType: EventType, invoker: EntityID<Int>?=null, target: EntityID<Int>?=null,
                      channelId: Int?=null, clientId: Int?=null, timestamp: LocalDateTime = LocalDateTime.now(TIMEZONE)) {
        if (invoker != null || target != null){
            RecordedEvents.insert {
                it[RecordedEvents.clientId] = clientId
                it[RecordedEvents.channelId] = channelId
                it[RecordedEvents.targetId] = target
                it[RecordedEvents.invokerId] = invoker
                it[RecordedEvents.eventType] = eventType.id
                it[RecordedEvents.timestamp] = timestamp
            }
        } else{
            logger.debug("Event was not recorded: invoker={}, target={}",
            invoker, target)
        }
    }
    /**
     * @return the UserId created in the database
     */
    private fun createUser(uniqueUserId: String): EntityID<Int>{
        return transaction {
            Users.insertOrIgnore{
                it[uniqueId] = uniqueUserId
                it[hasAgreed] = false
            }
            Users.slice(Users.id).select { Users.uniqueId eq uniqueUserId }
                .map {it[Users.id]}.first()
        }
    }

    fun registerUser(uniqueUserId: String, clientId: Int, timestamp: LocalDateTime = LocalDateTime.now(TIMEZONE)): EntityID<Int>? {
        return transaction {
            val userId = createUser(uniqueUserId)
            val agreed = User[userId].hasAgreed
            if (agreed) {
                logger.debug("User already has agreed to data collection")
                return@transaction null
            }

            UserRegistrations.insert {
                it[action] = UserEventType.UserRegistered.id
                it[UserRegistrations.timestamp] = timestamp
                it[user] =userId
            }
            User[userId].hasAgreed = true
            logger.debug("Registered user with userId: {}, UID: {}, clientID: {}", userId, uniqueUserId, clientId)

            database.registerEvent(
                EventType.ClientJoined,
                target = userId,
                invoker = null,
                clientId = clientId,
                timestamp = timestamp
            )
            userId
        }
    }

    fun unregisterUser(uniqueUserId: String, clientId: Int, timestamp: LocalDateTime = LocalDateTime.now(TIMEZONE)): EntityID<Int>? =
        transaction {
            val userId = createUser(uniqueUserId)
            val agreed = User[userId].hasAgreed
            if (!agreed) {
                logger.debug("User already not registered")
                return@transaction null
            }

            UserRegistrations.insert {
                it[action] = UserEventType.UserUnregistered.id
                it[UserRegistrations.timestamp] = timestamp
                it[user] = userId
            }
            User[userId].hasAgreed = false
            logger.debug("Unregistered user with userId: {}, UID: {}, clientID: {}", userId, uniqueUserId, clientId)

            database.registerEvent(
                EventType.ClientLeft,
                target= userId,
                invoker = null,
                clientId = clientId,
                timestamp = timestamp
            )
            userId
        }

    fun lastUserOfClientId(userId: Int): EntityID<Int>? {
        logger.debug("Querying for last clientId of user {}", userId)
        return transaction {
//            addLogger(StdOutSqlLogger)
            RecordedEvents.slice(RecordedEvents.targetId)
                .select { (RecordedEvents.eventType eq EventType.ClientJoined.id) and (RecordedEvents.clientId eq userId) }
                .orderBy(RecordedEvents.timestamp, SortOrder.DESC).limit(1)
                .map { it[RecordedEvents.targetId] }.firstOrNull()
        }
    }


    fun getUserId(uniqueUserId: String?, hasAgreed:Boolean=true): EntityID<Int>? {
        if (uniqueUserId == null || uniqueUserId == "")
            return null

        return transaction {
            User.find { (Users.uniqueId eq uniqueUserId) and (Users.hasAgreed eq hasAgreed) }.firstOrNull()?.id
        }
    }

    fun registerDatabaseMetaEvent(type: MetaEventType){
        logger.debug("Registering Database MetaEvent {}", type)
        transaction {
            MetaEvents.insert {
                it[metaEventType] = type.id
                it[timestamp] = LocalDateTime.now(TIMEZONE)
            }
        }
    }
}