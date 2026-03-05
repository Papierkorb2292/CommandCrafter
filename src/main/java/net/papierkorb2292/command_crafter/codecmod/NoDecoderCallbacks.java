package net.papierkorb2292.command_crafter.codecmod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes with this annotation will not be modified by the CommandCrafterDecoderOutputTrackerMixinCoprocessor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface NoDecoderCallbacks { }
