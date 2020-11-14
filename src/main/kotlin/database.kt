import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.`java-time`.timestamp

object Users : IdTable<String>() {
    val uniqueId = varchar("unique_id", 50).uniqueIndex()
    val hasAgreed = bool("has_agreed")
    override val id : Column<EntityID<String>> = uniqueId.entityId()
}

class User(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, User>(Users)
    var uniqueId by Users.uniqueId
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
    val invoker = reference("invoker", Users).nullable()
    val target = reference("target", Users).nullable()
    val obj1 = integer("obj1").nullable()
    val obj2 = integer("obj2").nullable()
    val timestamp = datetime("timestamp")
}

class RecordedEvent(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RecordedEvent>(RecordedEvents)

    var eventType by RecordedEvents.eventType
    var invoker by User optionalReferencedOn RecordedEvents.invoker
    var target by User optionalReferencedOn RecordedEvents.target
    var obj1 by RecordedEvents.obj1
    var obj2 by RecordedEvents.obj2
    var timestamp by RecordedEvents.timestamp
}