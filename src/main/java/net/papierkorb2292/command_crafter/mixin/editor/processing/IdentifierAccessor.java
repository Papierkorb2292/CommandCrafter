package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Identifier.class)
public interface IdentifierAccessor {
    @Invoker
    static boolean callIsNamespaceCharacterValid(char character) {
        throw new AssertionError();
    }
}
