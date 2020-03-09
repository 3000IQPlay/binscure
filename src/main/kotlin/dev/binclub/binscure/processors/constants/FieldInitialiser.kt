package dev.binclub.binscure.processors.constants

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.kotlin.hasAccess
import dev.binclub.binscure.utils.getClinit
import dev.binclub.binscure.utils.ldcDouble
import dev.binclub.binscure.utils.ldcInt
import dev.binclub.binscure.utils.ldcLong
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.tree.*

/**
 * @author cookiedragon234 07/Mar/2020
 */
object FieldInitialiser: IClassProcessor {
	override fun process(classes: MutableCollection<ClassNode>, passThrough: MutableMap<String, ByteArray>) {
		for (classNode in classes) {
			if (CObfuscator.isExcluded(classNode))
				continue
			
			val staticFields = arrayListOf<FieldNode>()
			val instanceFields = arrayListOf<FieldNode>()
			for (field in classNode.fields) {
				if (CObfuscator.isExcluded(classNode, field) || field.value == null)
					continue
				
				if (field.access.hasAccess(Opcodes.ACC_STATIC)) {
					staticFields
				} else {
					instanceFields
				}.add(field)
			}
			
			if (staticFields.isNotEmpty()) {
				val clinit = getClinit(classNode)
				clinit.instructions.apply {
					for (field in staticFields) {
						insert(FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, field.name, field.desc))
						insert(when (field.value) {
							is Int -> ldcInt(field.value as Int)
							is Double -> ldcDouble(field.value as Double)
							is Long -> ldcLong(field.value as Long)
							else -> LdcInsnNode(field.value)
						})
						
						field.value = null
					}
				}
			}
			
			if (instanceFields.isNotEmpty()) {
				val list = InsnList().apply {
					for (field in instanceFields) {
						add(VarInsnNode(ALOAD, 0))
						add(when (field.value) {
							is Int -> ldcInt(field.value as Int)
							is Double -> ldcDouble(field.value as Double)
							is Long -> ldcLong(field.value as Long)
							else -> LdcInsnNode(field.value)
						})
						add(FieldInsnNode(Opcodes.PUTFIELD, classNode.name, field.name, field.desc))
						
						field.value = null
					}
				}
				
				for (method in classNode.methods) {
					if (method.name == "<init>") {
						method.instructions?.insert(list)
					}
				}
			}
		}
	}
}