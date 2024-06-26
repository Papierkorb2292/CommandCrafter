package org.spongepowered.asm.mixin.transformer

import net.papierkorb2292.command_crafter.editor.processing.PreLaunchDecoderOutputTracker
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

internal class CommandCrafterDecoderOutputTrackerMixinCoprocessor : MixinCoprocessor() {
    private val decoderMethodName = "decode"
    private val decoderMethodDesc =
        "(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"

    override fun postProcess(name: String, classNode: ClassNode): Boolean {
        // I *should* check if the class implements `Decoder`, but that doesn't work for inner classes of
        // Mixins, because it's not possible to get a `ClassInfo` for them, so I'll just leave it. Consistency ;)

        val decodeMethod = classNode.methods.find {
            it.name == decoderMethodName && it.desc == decoderMethodDesc && it.access.and(Opcodes.ACC_ABSTRACT) == 0
        } ?: return false

        val returnInsns = mutableListOf<InsnNode>()
        var insnNode = decodeMethod.instructions.first
        while(insnNode != null) {
            if(insnNode.opcode == Opcodes.ARETURN)
                returnInsns.add(insnNode as InsnNode)
            insnNode = insnNode.next
        }

        val callbackObjectType = Type.getType(PreLaunchDecoderOutputTracker::class.java)

        for(returnInsn in returnInsns) {
            val errorChecking = InsnList()
            errorChecking.add(InsnNode(Opcodes.DUP))
            errorChecking.add(
                FieldInsnNode(
                    Opcodes.GETSTATIC,
                    callbackObjectType.internalName,
                    "INSTANCE",
                    callbackObjectType.descriptor
                )
            ) // Load INSTANCE
            errorChecking.add(InsnNode(Opcodes.SWAP))
            errorChecking.add(VarInsnNode(Opcodes.ALOAD, 2)) // Load input
            errorChecking.add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    callbackObjectType.internalName,
                    PreLaunchDecoderOutputTracker.ON_DECODED_NAME,
                    PreLaunchDecoderOutputTracker.ON_DECODED_DESC,
                    false
                )
            ) // Call INSTANCE.onDecoded(returnValue, input)

            decodeMethod.instructions.insertBefore(returnInsn, errorChecking)
        }
        return true
    }
}