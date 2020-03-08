package cookiedragon.obfuscator.api.transformers

import cookiedragon.obfuscator.api.TransformerConfiguration

/**
 * @author cookiedragon234 26/Jan/2020
 */
data class KotlinMetadataConfiguration(
	override val enabled: Boolean = false
): TransformerConfiguration(enabled)