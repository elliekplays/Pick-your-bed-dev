package pick.your.client;

import pick.your.respawn.RespawnEntryView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BedNameEditScreen extends Screen {
    private static final int BACKGROUND_TOP = 0x70000000;
    private static final int BACKGROUND_BOTTOM = 0xA8000000;
    private static final int PANEL_COLOR = 0xD82D343E;
    private static final int PANEL_HEADER = 0xCC343D49;
    private static final int PANEL_BORDER = 0xD067717E;
    private static final int ACCENT = 0xFF80C7D4;

    private final Screen parent;
    private final RespawnEntryView entry;
    private EditBox nameBox;
    private Button saveButton;
    private String draftName;

    public BedNameEditScreen(Screen parent, RespawnEntryView entry) {
        super(Component.literal("Edit Respawn Name"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        EditLayout layout = layout();
        String initialName = this.draftName == null ? this.entry.name() : this.draftName;

        this.nameBox = new EditBox(this.font, layout.left + 18, layout.nameBoxY, layout.panelWidth - 36, 20, Component.literal("Name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(initialName);
        this.nameBox.setResponder(value -> {
            this.draftName = value;
            if (this.saveButton != null) {
                this.saveButton.active = !value.trim().isEmpty();
            }
        });
        this.addRenderableWidget(this.nameBox);

        this.saveButton = this.addRenderableWidget(Button.builder(buttonLabel("Save", "Save", layout.saveWidth), button -> this.save())
            .bounds(layout.saveX, layout.saveY, layout.saveWidth, 20)
            .build());
        this.saveButton.active = !initialName.trim().isEmpty();
        this.addRenderableWidget(Button.builder(buttonLabel("Cancel", "Back", layout.cancelWidth), button -> this.close())
            .bounds(layout.cancelX, layout.cancelY, layout.cancelWidth, 20)
            .build());
        this.setInitialFocus(this.nameBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        EditLayout layout = layout();
        graphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, PANEL_COLOR);
        graphics.fill(layout.left + 1, layout.top + 1, layout.left + layout.panelWidth - 1, layout.top + 40, PANEL_HEADER);
        graphics.fill(layout.left, layout.top, layout.left + layout.panelWidth, layout.top + 1, ACCENT);
        graphics.fill(layout.left, layout.top + layout.panelHeight - 1, layout.left + layout.panelWidth, layout.top + layout.panelHeight, PANEL_BORDER);
        graphics.fill(layout.left, layout.top, layout.left + 1, layout.top + layout.panelHeight, PANEL_BORDER);
        graphics.fill(layout.left + layout.panelWidth - 1, layout.top, layout.left + layout.panelWidth, layout.top + layout.panelHeight, PANEL_BORDER);
        if (layout.showTitle) {
            graphics.drawString(this.font, "Edit name", layout.left + 18, layout.top + 14, 0xFFFFFFFF, false);
        }
        if (layout.showCoordinate) {
            graphics.drawString(this.font, trimToWidth(this.entry.coordinateText() + " in " + this.entry.dimensionText(), layout.panelWidth - 36), layout.left + 18, layout.top + 28, 0xFFB8C2CC, false);
        }
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
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
        if (this.nameBox == null) {
            return;
        }
        if (this.nameBox.getValue().trim().isEmpty()) {
            return;
        }

        PickYourBedClient.rename(this.entry.id(), this.nameBox.getValue());
        this.close();
    }

    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private EditLayout layout() {
        int screenWidth = Math.max(1, this.width);
        int screenHeight = Math.max(1, this.height);
        int panelWidth = clamp(screenWidth - 32, 180, 320);
        if (screenWidth < panelWidth + 12) {
            panelWidth = Math.max(112, screenWidth - 12);
        }
        boolean narrow = panelWidth < 230;
        int panelHeight = Math.min(narrow ? 146 : 122, Math.max(72, screenHeight - 16));
        int left = (screenWidth - panelWidth) / 2;
        int top = clamp(screenHeight / 2 - panelHeight / 2, 8, Math.max(8, screenHeight - panelHeight - 8));
        boolean showTitle = panelHeight >= 92;
        boolean showCoordinate = panelHeight >= 112;

        boolean stackedButtons = narrow && panelHeight >= 126;
        int saveWidth = stackedButtons ? Math.min(96, panelWidth - 36) : Math.max(34, Math.min(64, (panelWidth - 42) / 2));
        int cancelWidth = stackedButtons ? saveWidth : saveWidth;
        int saveX;
        int saveY;
        int cancelX;
        int cancelY;
        if (stackedButtons) {
            saveX = left + panelWidth / 2 - saveWidth / 2;
            saveY = top + panelHeight - 58;
            cancelX = left + panelWidth / 2 - cancelWidth / 2;
            cancelY = saveY + 24;
        } else {
            int buttonsWidth = saveWidth + 6 + cancelWidth;
            saveX = left + panelWidth / 2 - buttonsWidth / 2;
            saveY = top + panelHeight - 28;
            cancelX = saveX + saveWidth + 6;
            cancelY = saveY;
        }
        int preferredNameBoxY = showCoordinate ? top + 48 : showTitle ? top + 34 : top + 10;
        int nameBoxY = clamp(preferredNameBoxY, top + 8, Math.max(top + 8, saveY - 24));
        return new EditLayout(left, top, panelWidth, panelHeight, nameBoxY, saveX, saveY, saveWidth, cancelX, cancelY, cancelWidth, showTitle, showCoordinate);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        int ellipsisWidth = this.font.width("...");
        if (maxWidth <= ellipsisWidth) {
            return this.font.plainSubstrByWidth("...", maxWidth);
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        return this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + "...";
    }

    private Component buttonLabel(String full, String compact, int width) {
        return Component.literal(this.font.width(full) + 8 <= width ? full : compact);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record EditLayout(
        int left,
        int top,
        int panelWidth,
        int panelHeight,
        int nameBoxY,
        int saveX,
        int saveY,
        int saveWidth,
        int cancelX,
        int cancelY,
        int cancelWidth,
        boolean showTitle,
        boolean showCoordinate
    ) {
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
