package net.papierkorb2292.command_crafter.codecmod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adding this annotation to a method in a class that is part of the {@code command_crafter:codecmod} entrypoint
 * specifies a codec modification.
 * The handler method should be of the form
 * {@code static CodecType methodName(CodecType, FieldAccess1, ... FieldAccessN[, TargetMethodParam1, ... TargetMethodParamM]},
 * where CodecType is one of "MapCodec", "Codec" or "PrimitiveCodec", the FieldAccess types correspond with the types of the fields
 * specified in {@link #fieldAccess} and the optional TargetMethodParam types correspond with the parameters of the target method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface CodecMod {
    /**
     * The target class that contains the codec
     */
    Class<?> target() default void.class;

    /**
     * Alternative to {@link #target} that can be used if the class is not accessible
     */
    String targetName() default "";

    /**
     * The target method to modify. If no other modification is specified, the return value of the target method will be wrapped.
     */
    String methodName() default "";

    /**
     * The target field containing a codec.
     * If specified along with methodName, only field writes within that method will be searched.
     */
    String javaFieldWrite() default "";

    /**
     * The static target field (and its class) containing a codec.
     * If specified along with methodName, only field writes within that method will be searched.
     */
    String javaFieldRead() default "";

    /**
     * The target codec field to wrap (this string is expected to be given to fieldOf() or optionalFieldOf()).
     * If specified along with methodName, only codecs within that method will be searched.
     */
    String codecField() default "";

    /**
     * If true, codecField() will wrap the fieldOf() or optionalFieldOf() result. Otherwise, it will wrap the content of the field (the receiver)
     */
    boolean includeCodecField() default false;

    /**
     * Any additional shadow field values that the handler should receive. A value of "this" will give you the instance that the target method is called on, if it is not static.
     */
    String[] fieldAccess() default {};
}
