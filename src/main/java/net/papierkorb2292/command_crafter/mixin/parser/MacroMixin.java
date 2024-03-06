package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.function.Macro;
import net.minecraft.server.function.Procedure;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.parser.helper.FileLinesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Macro.class)
public class MacroMixin<T> {

    @ModifyReturnValue(
            method = "withMacroReplaced(Lnet/minecraft/nbt/NbtCompound;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;)Lnet/minecraft/server/function/Procedure;",
            at = @At("RETURN")
    )
    private Procedure<T> command_crafter$addDebugInformationToMacroReplaced(Procedure<T> procedure) {
        if(procedure instanceof DebugInformationContainer<?, ?> container) {
            command_crafter$copyDebugInformation(container);
        }
        if(procedure instanceof FileLinesContainer container) {
            var lines = ((FileLinesContainer)this).command_crafter$getLines();
            if(lines != null) {
                container.command_crafter$setLines(lines);
            }
        }
        return procedure;
    }

    private <TBreakpointLocation, TDebugFrame extends PauseContext.DebugFrame> void command_crafter$copyDebugInformation(DebugInformationContainer<TBreakpointLocation, TDebugFrame> container) {
        //noinspection unchecked
        var debugInformation = ((DebugInformationContainer<TBreakpointLocation, TDebugFrame>) this).command_crafter$getDebugInformation();
        if(debugInformation != null) {
            container.command_crafter$setDebugInformation(debugInformation);
        }
    }
}
