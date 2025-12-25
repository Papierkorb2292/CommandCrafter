package net.papierkorb2292.command_crafter.mixin.test;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameTestHelper.class)
public interface GameTestHelperAccessor {
    @Accessor
    public GameTestInfo getTestInfo();
}
