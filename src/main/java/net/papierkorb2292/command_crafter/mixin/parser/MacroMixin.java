package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.function.Macro;
import net.minecraft.server.function.Procedure;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ProcedureOriginalIdContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Macro.class)
public abstract class MacroMixin<T> {

    @Shadow public abstract Identifier id();

    @ModifyReturnValue(
            method = "withMacroReplaced(Lnet/minecraft/nbt/NbtCompound;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;)Lnet/minecraft/server/function/Procedure;",
            at = @At("RETURN")
    )
    private Procedure<T> command_crafter$addDebugInformationToMacroReplaced(Procedure<T> procedure) {
        if(procedure instanceof DebugInformationContainer<?, ?> container) {
            command_crafter$copyDebugInformation(container);
        }
        if(procedure instanceof FileSourceContainer container) {
            var lines = ((FileSourceContainer) this).command_crafter$getFileSourceLines();
            var fileId = ((FileSourceContainer) this).command_crafter$getFileSourceId();
            var fileType = ((FileSourceContainer) this).command_crafter$getFileSourceType();
            if(lines != null && fileId != null && fileType != null)
                container.command_crafter$setFileSource(lines, fileId, fileType);
        }
        if(procedure instanceof ProcedureOriginalIdContainer container) {
            container.command_crafter$setOriginalId(id());
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
