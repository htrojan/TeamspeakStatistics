import com.github.theholywaffle.teamspeak3.TS3Api
import com.github.theholywaffle.teamspeak3.TS3Config
import com.github.theholywaffle.teamspeak3.TS3Query
import com.github.theholywaffle.teamspeak3.api.ChannelProperty
import com.natpryce.konfig.Configuration
import java.lang.Exception

class TsController {
    lateinit var api: TS3Api;

    fun connectApi(host: String, user: String, password: String): TS3Api {
        val tsconfig: TS3Config = TS3Config()
        tsconfig.setHost(host)
        val query: TS3Query = TS3Query(tsconfig)
        query.connect()

        val api = query.api
        api.login(user, password)
        api.selectVirtualServerById(1)

        return api
    }

    fun connect(config: Configuration) {
        api = connectApi(config[teamspeak_host], config[teamspeak_user], config[teamspeak_password])
        val ownId = api.whoAmI().id
        val channel = api.getChannelByNameExact(config[channel_name], false)
        val channelId : Int = channel?.id
            ?: api.createChannel(config[channel_name], mapOf(
                ChannelProperty.CHANNEL_ORDER to "0",
                ChannelProperty.CHANNEL_DESCRIPTION to config[channel_description]))
        try {
            api.moveClient(ownId, channelId)
        }catch (e: Exception){
            println("Moving of client failed")
        }
    }

    fun spawnListener(listener: Listener) {
        api.addTS3Listeners(listener)
        api.registerAllEvents()
    }
}