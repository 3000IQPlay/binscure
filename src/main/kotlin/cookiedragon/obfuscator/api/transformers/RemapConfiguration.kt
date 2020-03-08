package cookiedragon.obfuscator.api.transformers

import cookiedragon.obfuscator.api.TransformerConfiguration

/**
 * @author cookiedragon234 26/Jan/2020
 */
data class RemapConfiguration(
	override val enabled: Boolean = false,
	val classes: Boolean = true,
	val methods: Boolean = true,
	val fields: Boolean = true,
	val classPrefix: String = "",
	val methodPrefix: String = "",
	val fieldPrefix: String = "",
	val localVariableName: String = "c",
	val localVariables: Boolean = true,
	val aggressiveOverloading: Boolean = false
): TransformerConfiguration(enabled) {
	fun areClassesEnabled() = classes && enabled
	fun areMethodsEnabled() = methods && enabled
	fun areFieldsEnabled() = fields && enabled
	fun areLocalsEnabled() = localVariables && enabled
}