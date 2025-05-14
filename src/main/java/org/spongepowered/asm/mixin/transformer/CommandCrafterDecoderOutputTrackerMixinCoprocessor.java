package org.spongepowered.asm.mixin.transformer;

import net.papierkorb2292.command_crafter.editor.processing.PreLaunchDecoderOutputTracker;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

// If this were a Kotlin class, it would throw a `NoClassDefFoundError` for `Intrinsics` due to the way it's loaded.
// In fact, this class doesn't seem to be able to access any Kotlin class and vice-versa
class CommandCrafterDecoderOutputTrackerMixinCoprocessor extends MixinCoprocessor {
    private final String decoderMethodName = "decode";
    private final String decoderMethodDesc = "(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;";
    private final String callbackObjectName = "net/papierkorb2292/command_crafter/editor/processing/PreLaunchDecoderOutputTracker";
    private final String callbackObjectDesc = "L" + callbackObjectName + ";";

    public boolean postProcess(String name, ClassNode classNode) {
        // I *should* check if the class implements `Decoder`, but that doesn't work for inner classes of
        // Mixins, because it's not possible to get a `ClassInfo` for them, so I'll just leave it. Consistency ;)

        MethodNode decodeMethod = null;
        for(final var method : classNode.methods) {
            if(method.name.equals(decoderMethodName) && method.desc.equals(decoderMethodDesc) && (method.access & Opcodes.ACC_ABSTRACT) == 0) {
                decodeMethod = method;
                break;
            }
        }
        if(decodeMethod == null) return false;

        addOnDecodeStartCall(decodeMethod);
        addOnDecodedCalls(decodeMethod);

        return true;
    }

    private void addOnDecodeStartCall(MethodNode decodeMethod) {
        final var callbackInjection = new InsnList();
        callbackInjection.add(
                new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        callbackObjectName,
                        "INSTANCE",
                        callbackObjectDesc
                )
        ); // Load INSTANCE
        callbackInjection.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Load input
        callbackInjection.add(
                new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        callbackObjectName,
                        PreLaunchDecoderOutputTracker.ON_DECODE_START_NAME,
                        PreLaunchDecoderOutputTracker.ON_DECODE_START_DESC,
                        false
                )
        ); // Call INSTANCE.onDecodeStart(input)

        decodeMethod.instructions.insert(callbackInjection);
    }

    private void addOnDecodedCalls(MethodNode decodeMethod) {
        final var returnInsns = new ArrayList<InsnNode>();
        var insnNode = decodeMethod.instructions.getFirst();
        while(insnNode != null) {
            if(insnNode.getOpcode() == Opcodes.ARETURN)
                returnInsns.add((InsnNode)insnNode);
            insnNode = insnNode.getNext();
        }

        for(final var returnInsn : returnInsns) {
            final var callbackInjection = new InsnList();
            callbackInjection.add(new InsnNode(Opcodes.DUP));
            callbackInjection.add(
                    new FieldInsnNode(
                            Opcodes.GETSTATIC,
                            callbackObjectName,
                            "INSTANCE",
                            callbackObjectDesc
                    )
            ); // Load INSTANCE
            callbackInjection.add(new InsnNode(Opcodes.SWAP));
            callbackInjection.add(new VarInsnNode(Opcodes.ALOAD, 2)); // Load input
            callbackInjection.add(
                    new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            callbackObjectName,
                            PreLaunchDecoderOutputTracker.ON_DECODED_NAME,
                            PreLaunchDecoderOutputTracker.ON_DECODED_DESC,
                            false
                    )
            ); // Call INSTANCE.onDecoded(returnValue, input)

            decodeMethod.instructions.insertBefore(returnInsn, callbackInjection);
        }
    }
}