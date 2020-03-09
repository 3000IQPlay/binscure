package dev.binclub.binscure.configuration

import com.sksamuel.hoplite.ConfigLoader
import dev.binclub.binscure.api.RootConfiguration
import java.io.File

/**
 * @author cookiedragon234 25/Jan/2020
 */
object ConfigurationManager {
	lateinit var rootConfig: RootConfiguration
	fun init(configFile: File) {
		rootConfig = ConfigLoader().loadConfigOrThrow(configFile.toPath())
	}
}