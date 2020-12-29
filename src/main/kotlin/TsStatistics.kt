import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import mu.KotlinLogging
import kotlin.concurrent.thread

val database: TsDatabase = TsDatabase()
val controller: TsController = TsController()

private val logger = KotlinLogging.logger {}
fun main() {
    logger.info("Starting...")
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database_stage.properties") overriding
            ConfigurationProperties.fromResource("query_stage.properties")


    while (true){
        try {
            logger.info("Trying to connect" )
            controller.connect(config)
            break
        } catch (e: Exception) {
            logger.info("Connection failed")
        }
    }

    logger.info("Connecting to database")
    database.connect(config)
    database.registerDatabaseMetaEvent(MetaEventType.ApplicationStarted)
    Runtime.getRuntime().addShutdownHook(Thread{
        database.registerDatabaseMetaEvent(MetaEventType.ApplicationShutdown)
    })
    logger.debug("Registered shutdown hook")
    controller.spawnListener(TextListener(database, config[channel_name], "Joker"))
    controller.spawnListener(JoinListener(database, config[channel_name]))
    controller.spawnListener(LeaveListener(database, config[channel_name]))
    logger.info("Successfully initialized all required queries and listeners")
}

