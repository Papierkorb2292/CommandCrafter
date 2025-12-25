package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.mixin.LevelResourceAccessor;
import net.papierkorb2292.command_crafter.parser.DatapackBuildArgs;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

@Mixin(DataPackCommand.class)
public class DataPackCommandMixin {

    @Shadow @Final private static DynamicCommandExceptionType ERROR_UNKNOWN_PACK;

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/tree/LiteralCommandNode;",
                    remap = false
            )
    )
    private static LiteralArgumentBuilder<CommandSourceStack> command_crafter$addDatapackBuildCommand(LiteralArgumentBuilder<CommandSourceStack> builder) {
        return builder.then(
                Commands.literal("build")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests((context, suggestionsBuilder) ->
                                        SharedSuggestionProvider.suggest(
                                            context.getSource().getServer().getPackRepository()
                                                    .getAvailablePacks().stream()
                                                    .map(Pack::getId)
                                                    .filter(id -> id.startsWith("file/"))
                                                    .map(StringArgumentType::escapeIfRequired),
                                            suggestionsBuilder)
                                )
                                .executes(context -> {
                                    command_crafter$buildDatapack(context, new DatapackBuildArgs.DatapackBuildArgsBuilder());
                                    return 1;
                                })
                                .then(Commands.argument("args", StringArgumentType.greedyString())
                                        .suggests(DatapackBuildArgs.DatapackBuildArgsParser.INSTANCE.getSUGGESTION_PROVIDER())
                                        .executes(context -> {
                                            var rawArgs = StringArgumentType.getString(context, "args");
                                            var parsedArgs = DatapackBuildArgs.DatapackBuildArgsParser.INSTANCE.parse(new StringReader(rawArgs));
                                            command_crafter$buildDatapack(context, parsedArgs);
                                            return 1;
                                        }))));
    }

    private static final LevelResource command_crafter$builtDatapackSavePath = LevelResourceAccessor.callConstructor("builtDatapacks");

    private static void command_crafter$buildDatapack(CommandContext<CommandSourceStack> context, DatapackBuildArgs.DatapackBuildArgsBuilder argsBuilder) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        if(!name.startsWith("file/")) {
            throw ERROR_UNKNOWN_PACK.create(name);
        }
        PackRepository resourcePackManager = context.getSource().getServer().getPackRepository();
        var packProfile = resourcePackManager.getPack(name);
        if(packProfile == null) {
            throw ERROR_UNKNOWN_PACK.create(name);
        }
        try(var pack = packProfile.open()) {
            if(!((pack instanceof FilePackResources) || (pack instanceof PathPackResources))) {
                throw ERROR_UNKNOWN_PACK.create(name);
            }
            if(argsBuilder.getPermissions() == null) {
                argsBuilder.setPermissions(context.getSource().getServer().getFunctionCompilationPermissions());
            }
            if(!name.endsWith(".zip")) {
                name += ".zip";
            }
            var output = context.getSource().getServer().getWorldPath(command_crafter$builtDatapackSavePath).resolve(name.substring(5)).toFile();
            //noinspection ResultOfMethodCallIgnored
            output.getParentFile().mkdirs();
            try(var outputStream = new FileOutputStream(output)) {
                var zipOutput = new ZipOutputStream(outputStream);
                //noinspection unchecked
                RawZipResourceCreator.Companion.buildDatapack(
                        pack,
                        argsBuilder.build(),
                        (CommandDispatcher<SharedSuggestionProvider>)(Object)context.getSource().getServer().getCommands().getDispatcher(),
                        zipOutput
                );
                zipOutput.close();
                context.getSource().sendSuccess(() -> Component.nullToEmpty("Successfully built datapack"), true);
            } catch (IOException e) {
                context.getSource().sendFailure(Component.nullToEmpty("Encountered IOException while building datapack. The exception is written to the game output."));
                CommandCrafter.INSTANCE.getLOGGER().error("Encountered IOException while building datapack", e);
            }
        }
    }
}
