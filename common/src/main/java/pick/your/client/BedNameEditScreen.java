package pick.your.client;

import pick.your.respawn.RespawnEntryView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BedNameEditScreen extends Screen {
    private final Screen parent;
    private final RespawnEntryView entry;
    private EditBox nameBox;
    private Button saveButton;

    public BedNameEditScreen(Screen parent, RespawnEntryView entry) {
        super(Component.literal("Edit Respawn Name"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(320, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = this.height / 2 - 58;

        this.nameBox = new EditBox(this.font, left + 18, top + 48, panelWidth - 36, 20, Component.literal("Name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(this.entry.name());
        this.nameBox.setResponder(value -> this.saveButton.active = !value.trim().isEmpty());
        this.addRenderableWidget(this.nameBox);

        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
            .bounds(left + panelWidth - 154, top + 84, 64, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.close())
            .bounds(left + panelWidth - 84, top + 84, 66, 20)
            .build());
        this.setInitialFocus(this.nameBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(320, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = this.height / 2 - 58;
        graphics.fill(left, top, left + panelWidth, top + 122, 0xEE17191D);
        graphics.fill(left, top, left + panelWidth, top + 1, 0xFF80C7D4);
        graphics.drawString(this.font, "Edit name", left + 18, top + 14, 0xFFFFFFFF, false);
        graphics.drawString(this.font, this.entry.coordinateText() + " in " + this.entry.dimensionText(), left + 18, top + 28, 0xFFB8C2CC, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            this.save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.close();
    }

    private void save() {
        PickYourBedClient.rename(this.entry.id(), this.nameBox.getValue());
        this.close();
    }

    private void close() {
        this.minecraft.setScreen(this.parent);
    }
}
