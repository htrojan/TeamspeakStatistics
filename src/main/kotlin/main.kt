import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding

val database: TsDatabase = TsDatabase()
val controller: TsController = TsController()

fun main() {
    print("Starting")
    val config = ConfigurationProperties.fromResource("teamspeak.properties") overriding
            ConfigurationProperties.fromResource("database_stage.properties") overriding
            ConfigurationProperties.fromResource("query.properties")

    database.connect(config)
    controller.connect(config)
    controller.spawnListener(Listener(controller.api, database))
}

