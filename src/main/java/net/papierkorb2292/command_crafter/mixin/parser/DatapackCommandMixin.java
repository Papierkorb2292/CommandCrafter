package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.DatapackCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.mixin.MinecraftServerAccessor;
import net.papierkorb2292.command_crafter.mixin.WorldSavePathAccessor;
import net.papierkorb2292.command_crafter.parser.DatapackBuildArgs;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

@Mixin(DatapackCommand.class)
public class DatapackCommandMixin {

    @Shadow @Final private static DynamicCommandExceptionType UNKNOWN_DATAPACK_EXCEPTION;

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/tree/LiteralCommandNode;",
                    remap = false
            )
    )
    private static LiteralArgumentBuilder<ServerCommandSource> command_crafter$addDatapackBuildCommand(LiteralArgumentBuilder<ServerCommandSource> builder) {
        return builder.then(
                CommandManager.literal("build")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .suggests((context, suggestionsBuilder) -> {
                                    List<String> candidates = new ArrayList<>();
                                    try {
                                        FileResourcePackProvider.forEachProfile(
                                                context.getSource().getServer().getSavePath(WorldSavePath.DATAPACKS),
                                                ((MinecraftServerAccessor)context.getSource().getServer()).getSession().getLevelStorage().getSymlinkFinder(),
                                                (path, pack) -> candidates.add(StringArgumentType.escapeIfRequired("file/" + path.getFileName().toString())));
                                    } catch (IOException e) {
                                        CommandCrafter.INSTANCE.getLOGGER().error("Encountered IOException while searching for buildable datapacks", e);
                                    }
                                    return CommandSource.suggestMatching(
                                            candidates.stream(),
                                            suggestionsBuilder);
                                })
                                .executes(context -> {
                                    command_crafter$buildDatapack(context, new DatapackBuildArgs.DatapackBuildArgsBuilder());
                                    return 1;
                                })
                                .then(CommandManager.argument("args", DatapackBuildArgs.DatapackBuildArgsArgumentType.INSTANCE)
                                        .executes(context -> {
                                            command_crafter$buildDatapack(
                                                    context,
                                                    DatapackBuildArgs.DatapackBuildArgsArgumentType.INSTANCE.getArgsBuilder(context, "args"));
                                            return 1;
                                        }))));
    }

    private static final WorldSavePath command_crafter$builtDatapackSavePath = WorldSavePathAccessor.callConstructor("builtDatapacks");

    private static void command_crafter$buildDatapack(CommandContext<ServerCommandSource> context, DatapackBuildArgs.DatapackBuildArgsBuilder argsBuilder) throws CommandSyntaxException {
        var name = StringArgumentType.getString(context, "name");
        if(!name.startsWith("file/")) {
            throw UNKNOWN_DATAPACK_EXCEPTION.create(name);
        }
        ResourcePackManager resourcePackManager = context.getSource().getServer().getDataPackManager();
        var packProfile = resourcePackManager.getProfile(name);
        if(packProfile == null) {
            throw UNKNOWN_DATAPACK_EXCEPTION.create(name);
        }
        try(var pack = packProfile.createResourcePack()) {
            if(!((pack instanceof ZipResourcePack) || (pack instanceof DirectoryResourcePack))) {
                throw UNKNOWN_DATAPACK_EXCEPTION.create(name);
            }
            if(argsBuilder.getPermissionLevel() == null) {
                argsBuilder.setPermissionLevel(context.getSource().getServer().getFunctionPermissionLevel());
            }
            if(!name.endsWith(".zip")) {
                name += ".zip";
            }
            var output = context.getSource().getServer().getSavePath(command_crafter$builtDatapackSavePath).resolve(name.substring(5)).toFile();
            //noinspection ResultOfMethodCallIgnored
            output.getParentFile().mkdirs();
            try(var outputStream = new FileOutputStream(output)) {
                var zipOutput = new ZipOutputStream(outputStream);
                //noinspection unchecked
                RawZipResourceCreator.Companion.buildDatapack(
                        pack,
                        argsBuilder.build(),
                        (CommandDispatcher<CommandSource>)(Object)context.getSource().getServer().getCommandManager().getDispatcher(),
                        zipOutput
                );
                zipOutput.close();
                context.getSource().sendFeedback(() -> Text.of("Successfully built datapack"), true);
            } catch (IOException e) {
                context.getSource().sendError(Text.of("Encountered IOException while building datapack. The exception is written to the game output."));
                CommandCrafter.INSTANCE.getLOGGER().error("Encountered IOException while building datapack", e);
            }
        }
    }
}
