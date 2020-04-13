package dev.binclub.binscure.utils

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.util.*

/**
 * @author cookiedragon234 12/Apr/2020
 */
object StackHeightCalculator {
	fun test(classNode: ClassNode, methodNode: MethodNode) {
		val out = calculateStackHeight(classNode, methodNode)
		for (insn in methodNode.instructions) {
			println(insn.opcodeString() + "\t\t" + out[insn])
		}
	}
	
	fun calculateStackHeight(classNode: ClassNode, methodNode: MethodNode) =
		calculateStackHeight(methodNode.instructions, methodNode.tryCatchBlocks, hashMapOf<Int, Type>().also {
			val static = methodNode.access.hasAccess(ACC_STATIC)
			if (!static) {
				it[0] = Type.getType("L${classNode.name};")
			}
			for ((i, type) in Type.getArgumentTypes(methodNode.desc).withIndex()) {
				if (static) {
					it[i] = type
				} else {
					it[i + 1] = type
				}
			}
		})
	
	private fun calculateHeightFromInsn(
		stack: Stack<Type>,
		abstractInsnNode: AbstractInsnNode,
		registers: MutableMap<Int, Type>,
		tryCatchBlockNodes: Collection<TryCatchBlockNode>,
		out: MutableMap<AbstractInsnNode, MutableList<Stack<Type>>>
	) {
		fun safeCalculateHeightFromInsn(
			stack: Stack<Type>,
			abstractInsnNode: AbstractInsnNode,
			registers: MutableMap<Int, Type>,
			tryCatchBlockNodes: Collection<TryCatchBlockNode>,
			out: MutableMap<AbstractInsnNode, MutableList<Stack<Type>>>
		) {
			try {
				calculateHeightFromInsn(stack, abstractInsnNode, registers, tryCatchBlockNodes, out)
			} catch (e: StackOverflowError) {
				//IllegalStateException("Found infinite loop while following stack", e).printStackTrace()
			}
		}
		
		fun alreadyCalculatedTarget(target: AbstractInsnNode): Boolean {
			return (out.containsKey(target))
		}
		
		var insn: AbstractInsnNode? = abstractInsnNode
		
		while (insn != null) {
			if (alreadyCalculatedTarget(insn)) return
			
			var next = insn.next
			
			out.getOrPutLazy(insn, {
				arrayListOf()
			}).add(stack.clone() as Stack<Type>)
			
			when (insn) {
				is JumpInsnNode -> {
					when (insn.opcode) {
						GOTO -> {
							next = null
						}
						IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> {
							stack.pop()
						}
						IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> {
							stack.pop()
							stack.pop()
						}
						else -> error("Unexpected ${insn.opcode}")
					}
					val stackAtTarget = stack.clone() as Stack<Type>
					
					safeCalculateHeightFromInsn(stackAtTarget, insn.label, registers, tryCatchBlockNodes, out)
				}
				is VarInsnNode -> {
					if (insn.opcode == RET) {
						error("RET not supported")
					} else {
						val info = varInfoMap[insn.opcode] ?: error("Unexpected opcode ${insn.opcode}")
						
						if (info.stores) {
							registers[insn.`var`] = stack.pop()
						} else {
							stack.push(registers[insn.`var`])
						}
					}
				}
				is LdcInsnNode -> {
					when (val cst = insn.cst) {
						is Int -> stack.push(Type.INT_TYPE)
						is Byte -> stack.push(Type.BYTE_TYPE)
						is Char -> stack.push(Type.CHAR_TYPE)
						is Short -> stack.push(Type.SHORT_TYPE)
						is Boolean -> stack.push(Type.BOOLEAN_TYPE)
						is Float -> stack.push(Type.FLOAT_TYPE)
						is Long -> stack.push(Type.LONG_TYPE)
						is Double -> stack.push(Type.DOUBLE_TYPE)
						is String -> stack.push(Type.getType("Ljava/lang/String;"))
						is Type -> {
							when (cst.sort) {
								Type.OBJECT -> stack.push(Type.getType("Ljava/lang/Class;"))
								Type.METHOD -> stack.push(Type.getType("Ljava/lang/invoke/MethodType;"))
								else -> stack.push(Type.getObjectType(cst.descriptor))
							}
						}
						is Handle -> stack.push(Type.getType("Ljava/lang/invoke/MethodHandle;"))
						is ConstantDynamic -> stack.push(Type.getType(cst.descriptor))
					}
				}
				is FieldInsnNode -> {
					val info = fieldInfoMap[insn.opcode] ?: error("Unexpected opcode ${insn.opcode}")
					
					if (info.stores) {
						stack.pop()
					} else {
						stack.push(Type.getType(insn.desc))
					}
				}
				is MethodInsnNode -> {
					for (arg in Type.getArgumentTypes(insn.desc)) {
						stack.pop()
					}
					
					if (insn.opcode == INVOKEVIRTUAL || insn.opcode == INVOKESPECIAL || insn.opcode == INVOKEINTERFACE) {
						stack.pop()
					}
					
					val retType = Type.getReturnType(insn.desc)
					if (retType != Type.VOID_TYPE) {
						stack.push(retType)
					}
				}
				is InvokeDynamicInsnNode -> {
					for (arg in Type.getArgumentTypes(insn.desc)) {
						stack.pop()
					}
					
					val retType = Type.getReturnType(insn.desc)
					if (retType != Type.VOID_TYPE) {
						stack.push(retType)
					}
				}
				is MultiANewArrayInsnNode -> {
					for (i in 0..insn.dims) {
						stack.pop()
					}
				}
				is IntInsnNode -> {
					when (insn.opcode) {
						BIPUSH -> {
							stack.push(Type.BYTE_TYPE)
						}
						SIPUSH -> {
							stack.push(Type.SHORT_TYPE)
						}
						NEWARRAY -> {
							stack.pop() // array size
							stack.push(Type.getType("[${primitiveTypeFromSort(insn.operand)}"))
						}
					}
				}
				is TypeInsnNode -> {
					when (insn.opcode) {
						NEW -> {
							stack.push(Type.getType("L${insn.desc};"))
						}
						ANEWARRAY -> {
							stack.pop() // array size
							stack.push(Type.getType("[L${insn.desc};")) // new array
						}
					}
				}
				is InsnNode -> {
					when (insn.opcode) {
						IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
							stack.pop() // index
							if (insn.opcode == AALOAD) {
								stack.push(Type.getType(stack.pop().descriptor.removePrefix("[")))
							} else {
								stack.pop() // arrayref
								stack.push(aloadTypeMap[insn.opcode]) // val
							}
						}
						IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> {
							stack.pop() // val
							stack.pop() // index
							stack.pop() // arrayref
						}
						ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
						LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1 -> {
							stack.push(constTypeMap[insn.opcode]) // value
						}
						POP -> {
							stack.pop()
						}
						POP2 -> {
							stack.pop()
							stack.pop()
						}
						DUP -> {
							stack.push(stack.peek())
						}
						DUP_X1 -> {
							val value1 = stack.pop()
							val value2 = stack.pop()
							stack.push(value1)
							stack.push(value2)
							stack.push(value1)
						}
						DUP_X2 -> {
							val value1 = stack.pop()
							val value2 = stack.pop()
							val value3 = stack.pop()
							stack.push(value1)
							stack.push(value3)
							stack.push(value2)
							stack.push(value1)
						}
						DUP2 -> {
							val value1 = stack.pop()
							val value2 = stack.pop()
							stack.push(value2)
							stack.push(value1)
							stack.push(value2)
							stack.push(value1)
						}
						DUP2_X1 -> {
							val value1 = stack.pop()
							val value2 = stack.pop()
							val value3 = stack.pop()
							stack.push(value2)
							stack.push(value1)
							stack.push(value3)
							stack.push(value2)
							stack.push(value1)
						}
						DUP2_X2 -> {
							val value1 = stack.pop()
							val value2 = stack.pop()
							val value3 = stack.pop()
							val value4 = stack.pop()
							stack.push(value2)
							stack.push(value1)
							stack.push(value4)
							stack.push(value3)
							stack.push(value2)
							stack.push(value1)
						}
						IADD, LADD, FADD, DADD,
						ISUB, LSUB, FSUB, DSUB,
						IMUL, LMUL, FMUL, DMUL,
						IDIV, LDIV, FDIV, DDIV,
						IREM, LREM, FREM, DREM,
						ISHL, LSHL, ISHR, LSHR,
						IUSHR, LUSHR,
						IAND, LAND, IOR, LOR,
						IXOR, LXOR -> {
							stack.pop()
							stack.pop()
							stack.push(arithmeticTypeMap[insn.opcode])
						}
						LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> {
							stack.pop()
							stack.pop()
							stack.push(Type.INT_TYPE)
						}
						IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> {
							stack.clear()
							next = null
						}
						MONITORENTER, MONITOREXIT -> {
							stack.pop()
						}
					}
				}
				is LookupSwitchInsnNode -> {
					stack.pop() // key
					
					for (label in insn.labels) {
						safeCalculateHeightFromInsn(
							stack.clone() as Stack<Type>,
							label,
							registers,
							tryCatchBlockNodes,
							out
						)
					}
					safeCalculateHeightFromInsn(
						stack.clone() as Stack<Type>,
						insn.dflt,
						registers,
						tryCatchBlockNodes,
						out
					)
					
					next = null
					stack.clear()
				}
				is TableSwitchInsnNode -> {
					stack.pop() // key
					
					for (label in insn.labels) {
						safeCalculateHeightFromInsn(
							stack.clone() as Stack<Type>,
							label,
							registers,
							tryCatchBlockNodes,
							out
						)
					}
					safeCalculateHeightFromInsn(
						stack.clone() as Stack<Type>,
						insn.dflt,
						registers,
						tryCatchBlockNodes,
						out
					)
					
					next = null
					stack.clear()
				}
			}
			
			
			insn = next
		}
	}
	
	fun calculateStackHeight(
		insnList: InsnList,
		tryCatchBlockNodes: Collection<TryCatchBlockNode>,
		registers: MutableMap<Int, Type> = hashMapOf()
	): Map<AbstractInsnNode, MutableList<Stack<Type>>> {
		val out = hashMapOf<AbstractInsnNode, MutableList<Stack<Type>>>()
		val insn = insnList.first ?: return out
		
		try {
			calculateHeightFromInsn(Stack(), insn, registers, tryCatchBlockNodes, out)
		} catch (e: StackOverflowError) {
			IllegalStateException("Found infinite loop while following stack", e).printStackTrace()
		}
		
		for (tryCatch in tryCatchBlockNodes) {
			try {
				calculateHeightFromInsn(Stack<Type>().also {
					it.push(Type.getType("L${tryCatch.type ?: "java/lang/Throwable"};"))
				}, tryCatch.handler, registers, tryCatchBlockNodes, out)
			} catch (e: StackOverflowError) {
				IllegalStateException("Found infinite loop while following stack", e).printStackTrace()
			}
		}
		
		return out
	}
}

private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")
private val NULL_TYPE = Type.getObjectType("null")

private data class VarInsnInfo(
	val stores: Boolean,
	val type: Type
)

private val varInfoMap = hashMapOf(
	ILOAD to VarInsnInfo(false, Type.INT_TYPE),
	LLOAD to VarInsnInfo(false, Type.LONG_TYPE),
	FLOAD to VarInsnInfo(false, Type.FLOAT_TYPE),
	DLOAD to VarInsnInfo(false, Type.DOUBLE_TYPE),
	ALOAD to VarInsnInfo(false, OBJECT_TYPE),
	ISTORE to VarInsnInfo(true, Type.INT_TYPE),
	LSTORE to VarInsnInfo(true, Type.LONG_TYPE),
	FSTORE to VarInsnInfo(true, Type.FLOAT_TYPE),
	DSTORE to VarInsnInfo(true, Type.DOUBLE_TYPE),
	ASTORE to VarInsnInfo(true, OBJECT_TYPE)
)

private inline class FieldInsnInfo(
	val stores: Boolean
)

private val fieldInfoMap = hashMapOf(
	GETSTATIC to FieldInsnInfo(false),
	PUTSTATIC to FieldInsnInfo(true),
	GETFIELD to FieldInsnInfo(false),
	PUTFIELD to FieldInsnInfo(true)
)
private val aloadTypeMap = hashMapOf(
	IALOAD to Type.INT_TYPE,
	LALOAD to Type.LONG_TYPE,
	FALOAD to Type.FLOAT_TYPE,
	DALOAD to Type.DOUBLE_TYPE,
	AALOAD to OBJECT_TYPE,
	BALOAD to Type.BYTE_TYPE,
	CALOAD to Type.CHAR_TYPE,
	SALOAD to Type.SHORT_TYPE
)
private val constTypeMap = hashMapOf(
	ACONST_NULL to NULL_TYPE,
	ICONST_M1 to Type.INT_TYPE,
	ICONST_0 to Type.INT_TYPE,
	ICONST_1 to Type.INT_TYPE,
	ICONST_2 to Type.INT_TYPE,
	ICONST_3 to Type.INT_TYPE,
	ICONST_4 to Type.INT_TYPE,
	ICONST_5 to Type.INT_TYPE,
	LCONST_0 to Type.LONG_TYPE,
	LCONST_1 to Type.LONG_TYPE,
	FCONST_0 to Type.FLOAT_TYPE,
	FCONST_1 to Type.FLOAT_TYPE,
	FCONST_2 to Type.FLOAT_TYPE,
	DCONST_0 to Type.DOUBLE_TYPE,
	DCONST_1 to Type.DOUBLE_TYPE
)
private val arithmeticTypeMap = hashMapOf(
	IADD to Type.INT_TYPE,
	LADD to Type.LONG_TYPE,
	FADD to Type.FLOAT_TYPE,
	DADD to Type.DOUBLE_TYPE,
	ISUB to Type.INT_TYPE,
	LSUB to Type.LONG_TYPE,
	FSUB to Type.FLOAT_TYPE,
	DSUB to Type.DOUBLE_TYPE,
	IMUL to Type.INT_TYPE,
	LMUL to Type.LONG_TYPE,
	FMUL to Type.FLOAT_TYPE,
	DMUL to Type.DOUBLE_TYPE,
	IDIV to Type.INT_TYPE,
	LDIV to Type.LONG_TYPE,
	FDIV to Type.FLOAT_TYPE,
	DDIV to Type.DOUBLE_TYPE,
	IREM to Type.INT_TYPE,
	LREM to Type.LONG_TYPE,
	FREM to Type.FLOAT_TYPE,
	DREM to Type.DOUBLE_TYPE,
	ISHL to Type.INT_TYPE,
	LSHL to Type.LONG_TYPE,
	ISHR to Type.INT_TYPE,
	LSHR to Type.LONG_TYPE,
	IUSHR to Type.INT_TYPE,
	LUSHR to Type.LONG_TYPE,
	IAND to Type.INT_TYPE,
	LAND to Type.LONG_TYPE,
	IOR to Type.INT_TYPE,
	LOR to Type.LONG_TYPE,
	IXOR to Type.INT_TYPE,
	LXOR to Type.LONG_TYPE
)