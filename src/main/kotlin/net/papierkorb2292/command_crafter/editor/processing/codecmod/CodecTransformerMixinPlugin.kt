package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.llamalad7.mixinextras.expression.Definition
import com.llamalad7.mixinextras.expression.Expression
import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import net.fabricmc.loader.api.FabricLoader
import net.papierkorb2292.command_crafter.codecmod.CodecMod
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import org.spongepowered.asm.util.Bytecode
import java.net.URL
import kotlin.reflect.KClass

/**
 * Applies @[CodecMod] injections by generating mixins at runtime
 */
class CodecTransformerMixinPlugin : IMixinConfigPlugin {
    val modifyReturnValueTemplateName = "net/papierkorb2292/command_crafter/codecmod/ModifyReturnValueTemplateMixin.class"
    val modifyJavaFieldTemplateName = "net/papierkorb2292/command_crafter/codecmod/ModifyJavaFieldTemplateMixin.class"
    val wrapCodecFieldTemplateName = "net/papierkorb2292/command_crafter/codecmod/WrapCodecFieldTemplateMixin.class"

    private val generatedMixinClasses = mutableMapOf<String, ByteArray>()
    private val generatedMixinNames = mutableListOf<String>()

    private fun getTemplate(name: String): ClassNode {
        val template = ClassNode()
        ClassReader(this.javaClass.classLoader.getResource("net/papierkorb2292/command_crafter/codecmod/$name.class")!!.readBytes())
            .accept(template, 0)
        return template

    }

    override fun onLoad(mixinPackage: String) {
        registerMixins()
        val modifyReturnValueTemplate = getTemplate("ModifyReturnValueTemplateMixin")
        val modifyReturnValueInterfaceTemplate = getTemplate("ModifyReturnValueInterfaceTemplateMixin")
        val modifyJavaFieldTemplate = getTemplate("ModifyJavaFieldTemplateMixin")
        val modifyJavaFieldInterfaceTemplate = getTemplate("ModifyJavaFieldInterfaceTemplateMixin")
        val wrapCodecFieldTemplate = getTemplate("WrapCodecFieldTemplateMixin")
        val wrapCodecFieldInterfaceTemplate = getTemplate("WrapCodecFieldInterfaceTemplateMixin")

        // Find all transformers specified through the entrypoint
        val transformers = FabricLoader.getInstance().getEntrypointContainers("command_crafter:codecmod", Any::class.java)
        for(transformerContainer in transformers) {
            val transformerName = transformerContainer.definition.replace(".", "/") + ".class"
            val transformerRaw = this.javaClass.classLoader.getResource(transformerName)?.readBytes()
                ?: continue // Ignore null for when it's clientside transformer, but we're on the server or vice versa

            val transformer = ClassNode()
            ClassReader(transformerRaw).accept(transformer, 0)

            for(method in transformer.methods) {
                val codecMod = method.invisibleAnnotations
                    ?.firstOrNull { it.desc == CodecMod::class.java.descriptorString() }
                    ?: continue

                if(!Bytecode.isStatic(method)) {
                    throw IllegalArgumentException("CodecMod handler must be static at ${transformer.name}::${method.name}")
                }

                val invoke = MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    transformer.name,
                    method.name,
                    method.desc
                )
                val argumentTypes = Type.getArgumentTypes(method.desc)
                val returnType = Type.getReturnType(invoke.desc)

                val codecModData = parseCodecMod(codecMod, transformer.name, method.name)
                val targetClassRaw = this.javaClass.classLoader.getResource(codecModData.target!!.internalName + ".class")!!.readBytes()
                val targetClass = ClassNode()
                ClassReader(targetClassRaw).accept(targetClass, 0)
                val targetsInterface = targetClass.access.and(Opcodes.ACC_INTERFACE) != 0
                val mixin = ClassNode()

                // Add mixin boilerplate
                val template = when(codecModData.type) {
                    CodecModType.MODIFY_JAVA_FIELD -> if(targetsInterface) modifyJavaFieldInterfaceTemplate else modifyJavaFieldTemplate
                    CodecModType.MODIFY_RETURN_VALUE -> if(targetsInterface) modifyReturnValueInterfaceTemplate else modifyReturnValueTemplate
                    CodecModType.WRAP_CODEC_FIELD -> if(targetsInterface) wrapCodecFieldInterfaceTemplate else wrapCodecFieldTemplate
                    else -> throw IllegalStateException()
                }
                template.accept(mixin)

                val transformerClassName = transformer.name.substringAfterLast('/')
                val mixinClassName = "CodecMod${transformerClassName}_${method.name}"
                mixin.name = "$mixinPackage.$mixinClassName".replace('.', '/')
                updateAnnotationValue(mixin.invisibleAnnotations, Mixin::class, "value", listOf(codecModData.target))
                if(targetsInterface && codecModData.fieldAccess.any { it != "this" })
                    throw IllegalArgumentException("Can't shadow interface fields")
                if(!targetsInterface)
                    fillShadows(codecModData, mixin, targetClass, argumentTypes)

                // Configure injection
                val injectionHandler = mixin.methods.first { it.name == "injectionHandler" }
                val mixinArguments = argumentTypes.toMutableList()
                mixinArguments.subList(1, 1 + codecModData.fieldAccess.size).clear()
                if(argumentTypes[0].sort != Type.OBJECT) {
                    throw IllegalArgumentException("First argument type must be reference type at ${transformer.name}::${method.name}")
                }
                injectionHandler.desc = Type.getMethodDescriptor(argumentTypes[0], *mixinArguments.toTypedArray())
                injectionHandler.name = "command_crafter$${method.name}"

                when(codecModData.type) {
                    CodecModType.MODIFY_JAVA_FIELD -> {
                        if(returnType != argumentTypes[0]) {
                            throw IllegalArgumentException("Handler does not return same type as first argument at ${transformer.name}::${method.name}")
                        }
                        // Find method that writes to the specified field, if none is given
                        if(codecModData.methodName.isEmpty()) {
                            val method = targetClass.methods.find { method ->
                                method.instructions.any { it is FieldInsnNode && it.opcode == Opcodes.PUTSTATIC && it.name == codecModData.javaFieldWrite }
                            } ?: throw IllegalArgumentException("Could not find field write for ${transformer.name}::${method.name}")
                            codecModData.methodName = method.name
                        }
                        updateAnnotationValue(injectionHandler.visibleAnnotations, ModifyExpressionValue::class, "method", codecModData.methodName)
                        updateAnnotationValue(injectionHandler.invisibleAnnotations, Definition::class, "field", codecModData.javaFieldWrite)
                        injectCall(injectionHandler, mixin, targetClass, invoke, codecModData, argumentTypes)
                    }
                    CodecModType.MODIFY_RETURN_VALUE -> {
                        if(returnType != argumentTypes[0]) {
                            throw IllegalArgumentException("Handler does not return same type as first argument at ${transformer.name}::${method.name}")
                        }
                        updateAnnotationValue(injectionHandler.visibleAnnotations, ModifyReturnValue::class, "method", codecModData.methodName)
                        injectCall(injectionHandler, mixin, targetClass, invoke, codecModData, argumentTypes)
                    }
                    CodecModType.WRAP_CODEC_FIELD -> {
                        if(returnType != argumentTypes[0]) {
                            throw IllegalArgumentException("Handler does not return same type as first argument at ${transformer.name}::${method.name}")
                        }
                        // Find method that adds the specified field, if none is given
                        if(codecModData.methodName.isEmpty()) {
                            val method = targetClass.methods.find { method ->
                                method.instructions.any { it is LdcInsnNode && it.cst == codecModData.codecField }
                            } ?: throw IllegalArgumentException("Could not find codec field for ${transformer.name}::${method.name}")
                            codecModData.methodName = method.name
                        }
                        updateAnnotationValue(injectionHandler.visibleAnnotations, ModifyExpressionValue::class, "method", codecModData.methodName)
                        val targetExpression = if(codecModData.includeCodecField) "?.?('${codecModData.codecField}')" else "@(?).?('${codecModData.codecField}')"
                        updateAnnotationValue(injectionHandler.invisibleAnnotations, Expression::class, "value", targetExpression)
                        injectCall(injectionHandler, mixin, targetClass, invoke, codecModData, argumentTypes)
                    }
                    else -> throw IllegalStateException()
                }

                val mixinWriter = ClassWriter(0)
                mixin.accept(mixinWriter)

                generatedMixinClasses["/${mixin.name}.class"] = mixinWriter.toByteArray()
                generatedMixinNames += mixinClassName
            }
        }
    }

    private fun updateAnnotationValue(annotations: List<AnnotationNode>, annotationClass: KClass<*>, fieldName: String, value: Any) {
        val annotation = annotations.first { it.desc == annotationClass.java.descriptorString() }
        for(i in 0 until annotation.values.size step 2) {
            if(annotation.values[i] == fieldName) {
                annotation.values[i + 1] = value
                break
            }
        }
    }

    private fun injectCall(method: MethodNode, mixin: ClassNode, target: ClassNode, invoke: MethodInsnNode, codecModData: CodecModData, handlerArguments: Array<Type>) {
        val isTargetStatic = Bytecode.isStatic(
            target.methods.find { it.name == codecModData.methodName }
                ?: throw IllegalArgumentException("Method ${method.name} does not exist")
        )
        if(isTargetStatic) {
            method.access = method.access or Opcodes.ACC_STATIC
            (method.instructions.first { it.opcode == Opcodes.ALOAD } as VarInsnNode).`var` -= 1 // No more 'this' parameter
        }
        val returnInsn = method.instructions.find { it.opcode == Opcodes.ARETURN }
        val call = InsnList()
        // Load shadows
        for(i in codecModData.fieldAccess.indices) {
            val field = codecModData.fieldAccess[i]
            if(field == "this") {
                if(isTargetStatic)
                    throw IllegalArgumentException("Can't access `this` in a static method")
                call.add(VarInsnNode(Opcodes.ALOAD, 0))
                continue
            }
            val opcode = if(Bytecode.isStatic(target.fields.first { it.name == field })) Opcodes.GETSTATIC else Opcodes.GETFIELD
            if(opcode == Opcodes.GETFIELD) {
                if(isTargetStatic)
                    throw IllegalArgumentException("Codec mod can't access non-static field $field in static method")
                call.add(VarInsnNode(Opcodes.ALOAD, 0)) // Load `this`
            }
            call.add(FieldInsnNode(opcode, mixin.name, field, handlerArguments[i+1].descriptor))
        }
        // Load target method parameters
        for(i in 1 until (handlerArguments.size - codecModData.fieldAccess.size)) {
            val type = handlerArguments[i + codecModData.fieldAccess.size]
            call.add(VarInsnNode(type.getOpcode(Opcodes.ILOAD), if(isTargetStatic) i else i + 1))
        }
        call.add(invoke)
        method.instructions.insertBefore(returnInsn, call)
    }

    private fun fillShadows(codecModData: CodecModData, mixin: ClassNode, target: ClassNode, argumentTypes: Array<Type>) {
        val templateIndex = mixin.fields.indexOfFirst { it.name == "shadow" }
        val template = mixin.fields.removeAt(templateIndex)
        for(i in codecModData.fieldAccess.indices) {
            val name = codecModData.fieldAccess[i]
            if(name == "this") continue // Special case: Will give `this` instance, doesn't need field
            val sourceField = target.fields.find { it.name == name }
                ?: throw IllegalArgumentException("Could not find shadow field for $name")
            template.accept(mixin)
            val shadow = mixin.fields.last()
            shadow.name = name
            shadow.desc = argumentTypes[i + 1].descriptor
            shadow.access = sourceField.access
        }
    }

    private fun parseCodecMod(codecMod: AnnotationNode, sourceClass: String, sourceMethod: String): CodecModData {
        val data = CodecModData()
        for(i in 0 until codecMod.values.size step 2) {
            @Suppress("UNCHECKED_CAST")
            when(codecMod.values[i]) {
                "target" -> data.target = codecMod.values[i+1] as Type
                "targetName" -> data.target = Type.getType(codecMod.values[i+1] as String)
                "methodName" -> data.methodName = codecMod.values[i+1] as String
                "javaFieldWrite" -> data.javaFieldWrite = codecMod.values[i+1] as String
                "codecField" -> data.codecField = codecMod.values[i+1] as String
                "includeCodecField" -> data.includeCodecField = codecMod.values[i+1] as Boolean
                "fieldAccess" -> data.fieldAccess = codecMod.values[i+1] as List<String>
            }
        }
        // A valid annotation must have one clear type. methodName is allowed to be optionally specified with the other
        // values to target a specific method instead of finding it automatically.
        if(data.methodName.isEmpty() && data.javaFieldWrite.isEmpty() && data.codecField.isEmpty()) {
            throw IllegalArgumentException("CodecMod is missing location at $sourceClass::$sourceMethod")
        }
        if(data.javaFieldWrite.isNotEmpty() && data.codecField.isNotEmpty()) {
            throw IllegalArgumentException("CodecMod can't target both java field and codec field at $sourceClass::$sourceMethod")
        }
        if(data.javaFieldWrite.isNotEmpty())
            data.type = CodecModType.MODIFY_JAVA_FIELD
        else if(data.codecField.isNotEmpty())
            data.type = CodecModType.WRAP_CODEC_FIELD
        else
            data.type = CodecModType.MODIFY_RETURN_VALUE
        return data
    }

    private fun registerMixins() {
        val classLoader = this.javaClass.classLoader
        for(method in classLoader.javaClass.methods) {
            if(method.returnType == Void.TYPE && method.parameters.size == 1 && method.parameters[0].type == URL::class.java) {
                // Probably the addURL method
                method.isAccessible = true
                method.invoke(classLoader, MixinGenStreamHandler(generatedMixinClasses).toURL())
                return
            }
        }
        throw IllegalStateException("No addURL method for mixin gen found")
    }

    override fun getRefMapperConfig(): String? = null

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean = true

    override fun acceptTargets(
        myTargets: Set<String>,
        otherTargets: Set<String>,
    ) { }

    override fun getMixins(): List<String> = generatedMixinNames

    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo,
    ) { }

    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo,
    ) { }

    private class CodecModData(
        var target: Type? = null,
        var type: CodecModType? = null,
        var methodName: String = "",
        var javaFieldWrite: String = "",
        var codecField: String = "",
        var includeCodecField: Boolean = false,
        var fieldAccess: List<String> = emptyList(),
    )

    private enum class CodecModType {
        MODIFY_RETURN_VALUE,
        WRAP_CODEC_FIELD,
        MODIFY_JAVA_FIELD
    }
}