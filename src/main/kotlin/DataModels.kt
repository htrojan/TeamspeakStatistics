import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime

object Users : IntIdTable() {
    val uniqueId = varchar("unique_id", 50).uniqueIndex()
    val hasAgreed = bool("has_agreed")
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var userUniqueId by Users.uniqueId
    var hasAgreed by Users.hasAgreed
}

object UserRegistrations : IntIdTable() {
    val user = reference("user", Users)
    val timestamp = datetime("timestamp")
    val action = integer("action")
}

class UserRegistration(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRegistration>(UserRegistrations)

    var user by User referencedOn  UserRegistrations.user
    var timestamp by UserRegistrations.timestamp
    var action by UserRegistrations.action
}

object RecordedEvents : IntIdTable() {
    val eventType = integer("event_type")
    val invokerId = reference("invoker_id", Users).nullable()
    val targetId = reference("target_id", Users).nullable()
    val clientId = integer("client_id").nullable()
    val channelId = integer("channel_id").nullable()
    val timestamp = datetime("timestamp")
}

class RecordedEvent(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RecordedEvent>(RecordedEvents)

    var eventType by RecordedEvents.eventType
    var invoker by User optionalReferencedOn RecordedEvents.invokerId
    var target by User optionalReferencedOn RecordedEvents.targetId
    var clientId by RecordedEvents.clientId
    var channelId by RecordedEvents.channelId
    var timestamp by RecordedEvents.timestamp
}

enum class EventType(val id: Int){
    ClientJoined(1),
    ClientLeft(2),
    ClientMoved(3),
}

enum class UserEventType(val id: Int){
    UserRegistered(0),
    UserUnregistered(1)
}