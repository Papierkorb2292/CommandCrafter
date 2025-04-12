package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.CommandQueueEntry;
import net.minecraft.command.Frame;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.commons.lang3.mutable.MutableLong;
import org.spongepowered.asm.mixin.Mixin;

import java.io.FileWriter;
import java.io.IOException;

@Mixin(CommandManager.class)
public class CommandManagerMixin {
    @WrapMethod(method = "method_54314")
    private static void benchmark(String string, ContextChain contextChain, ServerCommandSource serverCommandSource, CommandExecutionContext context, Operation<Void> original) throws IOException {
        var startTime = new MutableLong();
        var file = new FileWriter("benchmark.txt");
        var count = 0d;
        for(var entity : serverCommandSource.getWorld().iterateEntities()) {
            count += 0.25;
        }
        var count2 = count;
        for(int i = 0; i < 10000; i++) {
            context.enqueueCommand(new CommandQueueEntry(new Frame(0, (a, b) -> {
            }, context.getEscapeControl(0)), (a, b) -> {
                var count3 = count2;
                for(var entity : serverCommandSource.getWorld().iterateEntities()) {
                    entity.addCommandTag("x");
                    if(count3-- <= 0) {
                        break;
                    };
                }
                startTime.setValue(System.nanoTime());
            }));
            original.call(string, contextChain, serverCommandSource, context);
            context.enqueueCommand(new CommandQueueEntry(new Frame(0, (a, b) -> {
            }, context.getEscapeControl(0)), (a, b) -> {
                var endTime = System.nanoTime();
                try {
                    file.write((endTime - startTime.longValue()) + "\n");
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        context.enqueueCommand(new CommandQueueEntry(new Frame(0, (a, b) -> {
        }, context.getEscapeControl(0)), (a, b) -> {
            try {
                file.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }
}
