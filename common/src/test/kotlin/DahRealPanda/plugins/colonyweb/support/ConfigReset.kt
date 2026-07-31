package DahRealPanda.plugins.colonyweb.support

import DahRealPanda.plugins.colonyweb.Config

/**
 * Gives a test the config a freshly started server would have.
 *
 * [Config] is a singleton of mutable fields the loader fills in from the config file. No loader
 * runs here, so without this the fields keep their zero values — and `sessionDays = 0` expires
 * every session the moment it is created, which reads as an auth bug rather than as a missing
 * fixture. The values below are the defaults declared in `Config.define`.
 */
object ConfigReset {

    fun applyDefaults() {
        Config.httpPort = 8723
        Config.bindAddress = "0.0.0.0"
        Config.refreshIntervalSeconds = 3
        Config.autoDownloadVanillaAssets = true
        Config.mapEnabled = true
        Config.mapRadius = 256
        Config.publicHost = ""
        Config.authEnabled = true
        Config.sessionDays = 30
        Config.loginCodeMinutes = 10
    }
}
