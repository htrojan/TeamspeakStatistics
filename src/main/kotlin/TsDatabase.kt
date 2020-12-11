import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class TsDatabase {
    fun initDatabase(dbconfig: Configuration) {
        Database.connect(
            "jdbc:postgresql://${dbconfig[database_host]}:${dbconfig[database_port]}/${dbconfig[database_name]}",
            user = dbconfig[database_user], password = dbconfig[database_password])
        transaction {
            SchemaUtils.create(Users, UserRegistrations, RecordedEvents)
        }
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
                it[user] = userQuery.map { it[id] }.first()
            }
            Users.update({Users.uniqueId eq uniqueUserId }) { it[hasAgreed] = true }
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


    fun uniqueUserToEntityId(uniqueUserId: String?, agreedCondition: Boolean =true): EntityID<Int>? {
        return if (uniqueUserId != null && uniqueUserId != "") Users.select{(Users.uniqueId eq uniqueUserId) and (Users.hasAgreed eq true)}.
        map { it[Users.id] }.firstOrNull() else null
    }

    fun userIdToEntityId(userId: Int?, agreedCondition: Boolean=true): EntityID<Int>? {
        return if (userId != null) Users.select{(Users.id eq userId) and (Users.hasAgreed eq true)}.
        map{it[Users.id]}.firstOrNull() else null
    }

}