package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.apache.commons.lang3.mutable.MutableLong;
import org.spongepowered.asm.mixin.Mixin;

import java.io.FileWriter;
import java.io.IOException;

@Mixin(Commands.class)
public class CommandsMixin {
    @WrapMethod(method = "method_54314")
    private static void benchmark(String string, ContextChain contextChain, CommandSourceStack serverCommandSource, ExecutionContext context, Operation<Void> original) throws IOException {
        var startTime = new MutableLong();
        var file = new FileWriter("benchmark.txt");
        var count = 0d;
        for(var entity : serverCommandSource.getLevel().getAllEntities()) {
            count += 0.25;
        }
        var count2 = count;
        for(int i = 0; i < 10000; i++) {
            context.queueNext(new CommandQueueEntry(new Frame(0, (a, b) -> {
            }, context.frameControlForDepth(0)), (a, b) -> {
                var count3 = count2;
                for(var entity : serverCommandSource.getLevel().getAllEntities()) {
                    entity.addTag("x");
                    if(count3-- <= 0) {
                        break;
                    };
                }
                startTime.setValue(System.nanoTime());
            }));
            original.call(string, contextChain, serverCommandSource, context);
            context.queueNext(new CommandQueueEntry(new Frame(0, (a, b) -> {
            }, context.frameControlForDepth(0)), (a, b) -> {
                var endTime = System.nanoTime();
                try {
                    file.write((endTime - startTime.longValue()) + "\n");
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        context.queueNext(new CommandQueueEntry(new Frame(0, (a, b) -> {
        }, context.frameControlForDepth(0)), (a, b) -> {
            try {
                file.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }
}
