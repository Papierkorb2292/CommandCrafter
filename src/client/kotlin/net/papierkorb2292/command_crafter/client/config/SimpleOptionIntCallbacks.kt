package net.papierkorb2292.command_crafter.client.config

import com.mojang.serialization.Codec
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.papierkorb2292.command_crafter.mixin.client.OptionInstanceAccessor
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

object SimpleOptionIntCallbacks : OptionInstance.ValueSet<Int> {

    override fun createButton(
        tooltipFactory: OptionInstance.TooltipSupplier<Int>,
        gameOptions: Options,
        x: Int,
        y: Int,
        width: Int,
        changeCallback: Consumer<Int>
    ): Function<OptionInstance<Int>, AbstractWidget> {
        return Function { option ->
            val textInput = EditBox(
                    Minecraft.getInstance().font,
                    width / 2,
                    0,
                    width / 2,
                    20,
                    Component.literal("Regex input")
            )
            textInput.setValue(option.get().toString())
            @Suppress("KotlinConstantConditions")
            val label = StringWidget(
                    0,
                    0,
                    width / 2 - 5,
                    20,
                    (option as OptionInstanceAccessor).caption,
                    Minecraft.getInstance().font
            )
            val container = object : AbstractContainerWidget(x, y, width, 20, Component.literal("")) {
                override fun children(): MutableList<out GuiEventListener> {
                    return mutableListOf(label, textInput);
                }

                override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
                    label.render(context, mouseX, mouseY, delta);
                    textInput.render(context, mouseX, mouseY, delta);
                }

                override fun updateWidgetNarration(builder: NarrationElementOutput) { }

                override fun contentHeight() = label.height + textInput.height

                override fun scrollRate() = Minecraft.getInstance().font.lineHeight.toDouble()

                override fun setX(x: Int) {
                    val deltaX = x - this.x;
                    label.x += deltaX;
                    textInput.x += deltaX;
                    super.setX(x);
                }

                override fun setY(y: Int) {
                    val deltaY = y - this.y;
                    label.y += deltaY;
                    textInput.y += deltaY;
                    super.setY(y);
                }
            };
            container.setTooltip(tooltipFactory.apply(option.get()))
            textInput.setResponder {
                val int = it.toInt()
                option.set(int)
                changeCallback.accept(int)
                container.setTooltip(tooltipFactory.apply(int))
            }
            textInput.setFilter {
                it.toIntOrNull() != null
            }
            container
        };
    }

    override fun validateValue(value: Int) = Optional.of(value)

    override fun codec(): Codec<Int> = Codec.INT
}

