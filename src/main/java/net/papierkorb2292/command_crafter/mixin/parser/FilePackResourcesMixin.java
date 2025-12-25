package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.packs.FilePackResources;
import net.papierkorb2292.command_crafter.parser.helper.ZipFileProvider;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;
import java.util.zip.ZipFile;

@Mixin(FilePackResources.class)
public class FilePackResourcesMixin implements ZipFileProvider {

    @Shadow @Final private FilePackResources.SharedZipFileAccess zipFileAccess;

    @NotNull
    @Override
    public ZipFile command_crafter$getZipFile() {
        return Objects.requireNonNull(zipFileAccess.getOrCreateZipFile());
    }
}
