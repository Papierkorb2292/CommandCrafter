package net.papierkorb2292.command_crafter.client;

import com.mojang.serialization.Codec
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.ContainerWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.option.GameOptions
import net.minecraft.client.option.SimpleOption
import net.minecraft.text.Text
import net.papierkorb2292.command_crafter.mixin.SimpleOptionAccessor
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

object SimpleOptionIntCallbacks : SimpleOption.Callbacks<Int> {

    override fun getWidgetCreator(
            tooltipFactory: SimpleOption.TooltipFactory<Int>,
            gameOptions: GameOptions,
            x: Int,
            y: Int,
            width: Int,
            changeCallback: Consumer<Int>
    ): Function<SimpleOption<Int>, ClickableWidget> {
        return Function { option ->
            val textInput = TextFieldWidget(
                    MinecraftClient.getInstance().textRenderer,
                    width / 2,
                    0,
                    width / 2,
                    20,
                    Text.literal("Regex input")
            )
            textInput.text = option.value.toString()
            @Suppress("KotlinConstantConditions")
            val label = TextWidget(
                    0,
                    0,
                    width / 2 - 5,
                    20,
                    (option as SimpleOptionAccessor).text,
                    MinecraftClient.getInstance().textRenderer
            )
            val container = object : ContainerWidget(x, y, width, 20, Text.literal("")) {
                override fun children(): MutableList<out Element> {
                    return mutableListOf(label, textInput);
                }

                override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                    label.render(context, mouseX, mouseY, delta);
                    textInput.render(context, mouseX, mouseY, delta);
                }

                override fun appendClickableNarrations(builder: NarrationMessageBuilder ) { }

                override fun getContentsHeightWithPadding() = label.height + textInput.height

                override fun getDeltaYPerScroll() = MinecraftClient.getInstance().textRenderer.fontHeight.toDouble()

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
            container.setTooltip(tooltipFactory.apply(option.value))
            textInput.setChangedListener {
                val int = it.toInt()
                option.value = int
                changeCallback.accept(int)
                container.setTooltip(tooltipFactory.apply(int))
            }
            textInput.setTextPredicate {
                it.toIntOrNull() != null
            }
            container
        };
    }

    override fun validate(value: Int) = Optional.of(value)

    override fun codec(): Codec<Int> = Codec.INT
}

