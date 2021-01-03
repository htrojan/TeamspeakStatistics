import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.github.theholywaffle.teamspeak3.api.event.TS3Listener
import com.natpryce.konfig.Configuration
import mu.KLogging
import mu.KotlinLogging
import java.lang.Exception

private val logger = KotlinLogging.logger {  }
class TsController {
    lateinit var api: TS3Api;
    lateinit var configuration: Configuration
    val apiConnections: MutableList<TS3Api> = mutableListOf()


    private fun spawnConnection(host: String, user: String, password: String): TS3Api {
        logger.debug { "Spawning query connection User=$user host=$host" }
        val tsconfig: TS3Config = TS3Config()
        tsconfig.setHost(host)
        val query: TS3Query = TS3Query(tsconfig)
        try {
            query.connect()
        }catch (e: Exception){
            logger.error("Error while spawning query connection ", e)
        }

        val api = query.api
        api.login(user, password)
        api.selectVirtualServerById(1)

        return api
    }

    fun connect(config: Configuration) {
        configuration = config
        api = spawnConnection(config[teamspeak_host], config[teamspeak_user], config[teamspeak_password])
        try {
            logger.debug("Setting controller query nickname")
            api.setNickname("Das Orakel")
        }catch (e: Exception){
            logger.error("Controller naming failed", e)
        }
        val ownId = api.whoAmI().id
        val channel = api.getChannelByNameExact(config[channel_name], false)
        val channelId : Int = channel?.id
            ?: api.createChannel(config[channel_name], mapOf(
                ChannelProperty.CHANNEL_ORDER to "0",
                ChannelProperty.CHANNEL_DESCRIPTION to config[channel_description]))
        try {
            api.moveClient(ownId, channelId)
        }catch (e: Exception){
            logger.error("Moving of client failed", e)
        }
    }

    fun spawnListener(listener: TsListener) {

        val spawnedApi = spawnConnection(configuration[teamspeak_host], configuration[teamspeak_user], configuration[teamspeak_password])
        apiConnections.add(spawnedApi)
        listener.api = spawnedApi
        listener.init()
        spawnedApi.addTS3Listeners(listener)
        spawnedApi.registerAllEvents()
    }
}