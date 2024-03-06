package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.resource.ZipResourcePack;
import net.papierkorb2292.command_crafter.parser.helper.ZipFileProvider;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;
import java.util.zip.ZipFile;

@Mixin(ZipResourcePack.class)
public class ZipResourcePackMixin implements ZipFileProvider {

    @Shadow @Final private ZipResourcePack.ZipFileWrapper zipFile;

    @NotNull
    @Override
    public ZipFile command_crafter$getZipFile() {
        return Objects.requireNonNull(zipFile.open());
    }
}
