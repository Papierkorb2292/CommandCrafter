package org.spongepowered.asm.mixin.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Copies all usages of ValueInput from ServerPlayer.readAdditionalSaveData into FallbackPlayer.readAdditionalSaveData, such that
 * it can be used to easily read player data if the original one threw an error (which is likely when other mods modify it due to the special handling of DummyWorld)
 */
public class CommandCrafterFallbackPlayerSaveDataExtractorMixinCoprocessor extends MixinCoprocessor {
    private static final String FALLBACK_PLAYER_CLASS = "net.papierkorb2292.command_crafter.editor.processing.string_range_tree.FallbackPlayer";
    private static final String SERVER_PLAYER_CLASS = "net/minecraft/server/level/ServerPlayer";
    private static final String VALUE_INPUT_DESC = "Lnet/minecraft/world/level/storage/ValueInput;";
    private static final String READ_ADDITIONAL_SAVE_DATA_NAME = "readAdditionalSaveData";
    private static final String READ_ADDITIONAL_SAVE_DATA_DESC = "(" + VALUE_INPUT_DESC + ")V";
    private static final Set<Integer> CONSTANT_OPCODES = Set.of(Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1, Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.LDC, Opcodes.GETSTATIC);

    @Override
    String getName() {
        return "command_crafter_fallback_player_save_data_extractor";
    }

    @Override
    public boolean couldTransform(String className) {
        return FALLBACK_PLAYER_CLASS.equals(className);
    }

    @Override
    public boolean postProcess(String name, ClassNode classNode) {
        if (!FALLBACK_PLAYER_CLASS.equals(name)) return false;

        MethodNode targetMethod = findMethod(classNode, READ_ADDITIONAL_SAVE_DATA_NAME, READ_ADDITIONAL_SAVE_DATA_DESC);
        if (targetMethod == null) return false;
        ClassNode serverPlayerNode = readClassNode(SERVER_PLAYER_CLASS);
        if (serverPlayerNode == null) return false;
        MethodNode sourceMethod = findMethod(serverPlayerNode, READ_ADDITIONAL_SAVE_DATA_NAME, READ_ADDITIONAL_SAVE_DATA_DESC);
        if (sourceMethod == null) return false;

        List<InsnList> injections = findValueInputCalls(sourceMethod);
        if (injections.isEmpty()) return false;

        AbstractInsnNode insertionPoint = findMethodInsertionPoint(targetMethod);
        for (InsnList injection : injections)
            targetMethod.instructions.insert(insertionPoint, injection);

        return true;
    }

    private List<InsnList> findValueInputCalls(MethodNode method) {
        List<InsnList> matches = new ArrayList<>();

        AbstractInsnNode current = method.instructions.getFirst();
        while (current != null) {
            if (current.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) current).var != 1) {
                current = current.getNext();
                continue;
            }
            List<AbstractInsnNode> sequence = new ArrayList<>();
            sequence.add(current);
            current = current.getNext();
            while (current != null) {
                if(current.getOpcode() == -1) {
                    current = current.getNext();
                    continue; // Labels or line numbers have an opcode of -1
                }
                if(!isConstant(current))
                    break;
                sequence.add(current);
                current = current.getNext();
            }

            if (current instanceof MethodInsnNode methodCall) {
                int loadedCount = sequence.size();
                Type[] argumentTypes = Type.getArgumentTypes(methodCall.desc);
                int expectedArgumentCount = argumentTypes.length;
                int actualArgumentCount = methodCall.getOpcode() == Opcodes.INVOKESTATIC
                        ? loadedCount
                        : loadedCount - 1;

                // Only copy the method if we actually have all arguments
                // Note that this also excludes super.readAdditionalSaveData
                if (actualArgumentCount == expectedArgumentCount && loadedCount > 0) {
                    InsnList injection = new InsnList();
                    for (AbstractInsnNode insn : sequence) {
                        injection.add(cloneInsn(insn));
                    }
                    injection.add(cloneInsn(methodCall));

                    Type returnType = Type.getReturnType(methodCall.desc);
                    if (!Type.VOID_TYPE.equals(returnType)) {
                        int popOpcode = (returnType.getSort() == Type.LONG || returnType.getSort() == Type.DOUBLE) ? Opcodes.POP2 : Opcodes.POP;
                        injection.add(new InsnNode(popOpcode));
                    }

                    matches.add(injection);
                }
                current = current.getNext();
            }
        }

        return matches;
    }

    private boolean isConstant(AbstractInsnNode insn) {
        return CONSTANT_OPCODES.contains(insn.getOpcode());
    }

    private AbstractInsnNode findMethodInsertionPoint(MethodNode method) {
        // Select opcode before return (readAdditionalSaveData is known to return void)
        AbstractInsnNode current = method.instructions.getLast();
        while (current.getOpcode() != Opcodes.RETURN)
            current = current.getPrevious();
        return current.getPrevious();
    }

    private MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }

        return null;
    }

    private ClassNode readClassNode(String internalName) {
        String resourceName = internalName + ".class";
        ClassLoader loader = getClass().getClassLoader();
        if (loader == null) {
            loader = CommandCrafterFallbackPlayerSaveDataExtractorMixinCoprocessor.class.getClassLoader();
        }

        try (InputStream inputStream = loader.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                return null;
            }

            ClassReader reader = new ClassReader(inputStream);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, ClassReader.SKIP_FRAMES);
            return classNode;
        } catch (IOException ignored) {
            return null;
        }
    }

    private AbstractInsnNode cloneInsn(AbstractInsnNode original) {
        return original.clone(Collections.emptyMap());
    }
}
