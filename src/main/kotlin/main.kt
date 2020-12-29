import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import kotlin.concurrent.thread

val database: TsDatabase = TsDatabase()
val controller: TsController = TsController()

fun main() {
    print("Starting")
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database.properties") overriding
            ConfigurationProperties.fromResource("query.properties")


    while (true){
        try {
            println("Trying to connect...")
            controller.connect(config)
            break
        } catch (e: Exception) {
            println(e.message)
        }
    }

    database.connect(config)
    database.registerDatabaseMetaEvent(MetaEventType.ApplicationStarted)
    Runtime.getRuntime().addShutdownHook(thread{
        database.registerDatabaseMetaEvent(MetaEventType.ApplicationShutdown)
    })
    controller.spawnListener(Listener(controller.api, database))
}

