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