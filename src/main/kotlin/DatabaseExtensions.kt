import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager



fun <T : Table> T.insertOrIgnore(vararg keys: Column<*>, body: T.(InsertStatement<Number>) -> Unit) =
    InsertOrIgnore<Number>(this, keys = keys).apply {
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