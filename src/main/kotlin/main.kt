import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import kotlin.concurrent.thread

val database: TsDatabase = TsDatabase()
val controller: TsController = TsController()

fun main() {
    println("Starting")
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database_stage.properties") overriding
            ConfigurationProperties.fromResource("query_stage.properties")


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
    Runtime.getRuntime().addShutdownHook(Thread{
        database.registerDatabaseMetaEvent(MetaEventType.ApplicationShutdown)
    })
    controller.spawnListener(TextListener(database, config[channel_name], "Joker"))
    controller.spawnListener(JoinListener(database, config[channel_name]))
    controller.spawnListener(LeaveListener(database, config[channel_name]))
}

