package net.papierkorb2292.command_crafter.mixin.test;

import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TestContext.class)
public interface TestContextAccessor {
    @Accessor
    public GameTestState getTest();
}
