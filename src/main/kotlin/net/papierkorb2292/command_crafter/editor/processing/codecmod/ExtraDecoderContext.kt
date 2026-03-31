package net.papierkorb2292.command_crafter.editor.processing.codecmod

import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.ParentLinks

/**
 * Filters ExtraDecoderBehavior to only methods/properties that provide extra context necessary for decoding ([registries],[getParent])
 */
class ExtraDecoderContext<TNode : Any>(private val delegate: ExtraDecoderBehavior<TNode>): ExtraDecoderBehavior<TNode> {
    override val registries: RegistryAccess?
        get() = delegate.registries

    override val parentLinks: ParentLinks?
        get() = delegate.parentLinks
}