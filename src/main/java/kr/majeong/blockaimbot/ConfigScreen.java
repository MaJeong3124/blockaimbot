package kr.majeong.blockaimbot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

public final class ConfigScreen extends Screen {
    private final Screen parent;
    private boolean saveFailed;
    public ConfigScreen(Screen parent) { super(Component.translatable("blockaimbot.settings")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 130;
        int y = Math.max(35, height / 2 - 75);
        AimConfig c = BlockAimClient.config;
        addRenderableWidget(new SettingSlider(x, y, "blockaimbot.speed", c.aimSpeed, 5, 720, n -> c.aimSpeed = n));
        addRenderableWidget(new SettingSlider(x, y + 26, "blockaimbot.distance", c.maxDistance, 1, 16, n -> c.maxDistance = n));
        addRenderableWidget(new SettingSlider(x, y + 52, "blockaimbot.randomization", c.randomization, 0, 50, n -> c.randomization = n));
        addRenderableWidget(Button.builder(Component.translatable("blockaimbot.controls"), b ->
                minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options)))
                .bounds(x, y + 82, 260, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 108, 260, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 16, 0xFFFFFFFF, true);
        Component note = Component.translatable(saveFailed ? "blockaimbot.save_failed" : "blockaimbot.random_note");
        graphics.text(font, note, width / 2 - font.width(note) / 2, Math.max(35, height / 2 - 75) + 136,
                saveFailed ? 0xFFFF5555 : 0xFFAAAAAA, true);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() { saveFailed = !BlockAimClient.config.save(); }

    private final class SettingSlider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final DoubleConsumer setter;
        SettingSlider(int x, int y, String label, double current, double min, double max, DoubleConsumer setter) {
            super(x, y, 260, 20, Component.empty(), (current - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.setter = setter;
            updateMessage();
        }
        @Override protected void updateMessage() {
            if (label != null) setMessage(Component.translatable(label, String.format(java.util.Locale.ROOT, "%.1f", min + value * (max - min))));
        }
        @Override protected void applyValue() {
            setter.accept(min + value * (max - min));
            saveFailed = !BlockAimClient.config.save();
        }
    }
}
