package net.papierkorb2292.command_crafter;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

public class MixinUtil {
    public static <Result, Throws extends Throwable> Result callWithThrows(Operation<Result> op, Object... args) throws Throws {
        return op.call(args);
    }
}
