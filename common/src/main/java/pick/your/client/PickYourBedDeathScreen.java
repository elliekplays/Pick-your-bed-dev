package pick.your.client;

import pick.your.respawn.RespawnEntryType;
import pick.your.respawn.RespawnEntryView;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

public class PickYourBedDeathScreen extends Screen {
    private static final int PANEL_COLOR = 0xE914171C;
    private static final int PANEL_BORDER = 0xFF2C3138;
    private static final int ACCENT = 0xFF80C7D4;
    private static final int SELECTED = 0xFF355A62;
    private static final int ROW = 0xBB222832;
    private static final int ROW_HOVER = 0xDD2B3440;
    private static final int INVALID_ROW = 0x99303136;

    private final Component causeOfDeath;
    private final boolean hardcore;
    private final List<Button> exitButtons = Lists.newArrayList();
    private Component deathScore = CommonComponents.EMPTY;
    private Button selectedRespawnButton;
    private RespawnFilter filter = RespawnFilter.ALL;
    private long selectedId = -1L;
    private int delayTicker;
    private int refreshTicker;
    private int scrollIndex;

    public PickYourBedDeathScreen(Component causeOfDeath, boolean hardcore) {
        super(Component.translatable(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.causeOfDeath = causeOfDeath;
        this.hardcore = hardcore;
    }

    @Override
    protected void init() {
        this.delayTicker = 0;
        this.refreshTicker = 0;
        this.exitButtons.clear();
        this.clearWidgets();
        PickYourBedClient.requestEntries();

        int panelWidth = panelWidth();
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = listTop();
        int filterY = panelTop + 12;
        int filterX = panelLeft + 14;
        for (RespawnFilter value : RespawnFilter.values()) {
            this.addRenderableWidget(Button.builder(Component.literal(value.label), button -> {
                this.filter = value;
                this.scrollIndex = 0;
            }).bounds(filterX, filterY, value.width, 18).build());
            filterX += value.width + 4;
        }

        int buttonY = Math.min(this.height - 56, panelTop + panelHeight() + 14);
        this.selectedRespawnButton = this.addRenderableWidget(Button.builder(Component.literal("Respawn at Selected"), button -> respawnAtSelected())
            .bounds(this.width / 2 - 154, buttonY, 150, 20)
            .build());
        this.exitButtons.add(this.selectedRespawnButton);

        Component normalLabel = this.hardcore ? Component.translatable("deathScreen.spectate") : Component.literal("Respawn Normally");
        this.exitButtons.add(this.addRenderableWidget(Button.builder(normalLabel, button -> {
            if (this.minecraft.player != null) {
                this.minecraft.player.respawn();
            }
            button.active = false;
        }).bounds(this.width / 2 + 4, buttonY, 150, 20).build()));

        this.exitButtons.add(this.addRenderableWidget(Button.builder(Component.translatable("deathScreen.titleScreen"), button -> handleExitToTitleScreen())
            .bounds(this.width / 2 - 75, buttonY + 24, 150, 20)
            .build()));

        setButtonsActive(false);
        updateSelectedButton();
        if (this.minecraft.player != null) {
            this.deathScore = Component.translatable(
                "deathScreen.score.value",
                Component.literal(Integer.toString(this.minecraft.player.getScore())).withStyle(ChatFormatting.YELLOW)
            );
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.delayTicker++;
        this.refreshTicker++;
        if (this.delayTicker == 20) {
            setButtonsActive(true);
        }
        if (this.refreshTicker >= 40) {
            this.refreshTicker = 0;
            PickYourBedClient.requestEntries();
        }
        updateSelectedButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.pose().pushPose();
        graphics.pose().scale(2.0F, 2.0F, 2.0F);
        graphics.drawCenteredString(this.font, this.title, this.width / 4, 24, 0xFFFFFFFF);
        graphics.pose().popPose();

        if (this.causeOfDeath != null) {
            graphics.drawCenteredString(this.font, this.causeOfDeath, this.width / 2, 70, 0xFFE8EDF2);
        }
        graphics.drawCenteredString(this.font, this.deathScore, this.width / 2, 84, 0xFFE8EDF2);

        renderList(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.causeOfDeath != null && mouseY > 70 && mouseY < 79) {
            Style style = this.getClickedComponentStyleAt(mouseX);
            graphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xD0180A0D, 0xF0030508);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.causeOfDeath != null && mouseY > 70.0 && mouseY < 79.0) {
            Style style = this.getClickedComponentStyleAt((int)mouseX);
            if (style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                this.handleComponentClicked(style);
                return false;
            }
        }

        RespawnEntryView hovered = hoveredEntry((int)mouseX, (int)mouseY);
        if (hovered != null && hovered.valid()) {
            int iconLeft = panelLeft() + panelWidth() - 30;
            int rowY = rowYFor(hovered);
            if (mouseX >= iconLeft && mouseX <= iconLeft + 18 && mouseY >= rowY + 3 && mouseY <= rowY + 21) {
                this.minecraft.setScreen(new BedNameEditScreen(this, hovered));
                return true;
            }

            this.selectedId = hovered.id();
            updateSelectedButton();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideList((int)mouseX, (int)mouseY)) {
            int max = Math.max(0, filteredEntries().size() - visibleRows());
            this.scrollIndex = Math.max(0, Math.min(max, this.scrollIndex - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = panelLeft();
        int top = listTop();
        int width = panelWidth();
        int height = panelHeight();
        graphics.fill(left, top, left + width, top + height, PANEL_COLOR);
        graphics.fill(left, top, left + width, top + 1, ACCENT);
        graphics.fill(left, top + height - 1, left + width, top + height, PANEL_BORDER);
        graphics.fill(left, top, left + 1, top + height, PANEL_BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, PANEL_BORDER);

        graphics.drawString(this.font, "Choose respawn point", left + 14, top + 36, 0xFFFFFFFF, false);

        List<RespawnEntryView> entries = filteredEntries();
        if (entries.isEmpty()) {
            String message = this.filter == RespawnFilter.BEDS ? "No beds recorded" : this.filter == RespawnFilter.OTHER ? "No other respawns recorded" : "No respawns recorded";
            graphics.drawCenteredString(this.font, Component.literal(message), left + width / 2, top + height / 2, 0xFF8F99A3);
            return;
        }

        int visible = visibleRows();
        int max = Math.max(0, entries.size() - visible);
        this.scrollIndex = Math.max(0, Math.min(max, this.scrollIndex));
        int startY = rowsTop();
        RespawnEntryView tooltipEntry = null;
        for (int i = 0; i < visible && i + this.scrollIndex < entries.size(); i++) {
            RespawnEntryView entry = entries.get(i + this.scrollIndex);
            int rowY = startY + i * 25;
            boolean hovered = mouseX >= left + 12 && mouseX <= left + width - 12 && mouseY >= rowY && mouseY <= rowY + 22;
            boolean selected = entry.id() == this.selectedId;
            int rowColor = entry.valid() ? (selected ? SELECTED : hovered ? ROW_HOVER : ROW) : INVALID_ROW;
            graphics.fill(left + 12, rowY, left + width - 12, rowY + 22, rowColor);
            graphics.fill(left + 12, rowY, left + 15, rowY + 22, entry.type() == RespawnEntryType.BED ? 0xFFE05B65 : 0xFFB48AF1);

            int textColor = entry.valid() ? 0xFFF2F5F7 : 0xFF848B92;
            int subColor = entry.valid() ? 0xFFAAB5BF : 0xFF6F767D;
            graphics.drawString(this.font, entry.name(), left + 22, rowY + 4, textColor, false);
            String subtitle = entry.type().displayName().getString() + " - " + entry.dimensionText();
            if (!entry.valid()) {
                subtitle = entry.invalidReason();
            }
            graphics.drawString(this.font, subtitle, left + 22, rowY + 14, subColor, false);

            if (entry.valid()) {
                drawPencil(graphics, left + width - 28, rowY + 5, hovered ? 0xFFFFFFFF : 0xFFB8C2CC);
            }

            if (hovered) {
                tooltipEntry = entry;
            }
        }

        if (entries.size() > visible) {
            int barTop = rowsTop();
            int barHeight = visible * 25 - 3;
            int thumbHeight = Math.max(14, barHeight * visible / entries.size());
            int thumbTop = barTop + (barHeight - thumbHeight) * this.scrollIndex / Math.max(1, max);
            graphics.fill(left + width - 7, barTop, left + width - 5, barTop + barHeight, 0x66333A42);
            graphics.fill(left + width - 8, thumbTop, left + width - 4, thumbTop + thumbHeight, 0xFF80C7D4);
        }

        if (tooltipEntry != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(tooltipEntry.name()));
            lines.add(Component.literal(tooltipEntry.type().displayName().getString()));
            lines.add(Component.literal("XYZ: " + tooltipEntry.coordinateText()));
            lines.add(Component.literal("Dimension: " + tooltipEntry.dimensionText()));
            lines.add(Component.literal(tooltipEntry.valid() ? "Ready to respawn" : tooltipEntry.invalidReason()));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void drawPencil(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 3, y + 9, x + 5, y + 11, 0xFFC6924E);
        graphics.fill(x + 5, y + 7, x + 7, y + 9, color);
        graphics.fill(x + 7, y + 5, x + 9, y + 7, color);
        graphics.fill(x + 9, y + 3, x + 11, y + 5, color);
        graphics.fill(x + 10, y + 2, x + 12, y + 4, 0xFFE8B86A);
    }

    private RespawnEntryView hoveredEntry(int mouseX, int mouseY) {
        if (!insideList(mouseX, mouseY)) {
            return null;
        }
        List<RespawnEntryView> entries = filteredEntries();
        int row = (mouseY - rowsTop()) / 25;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int index = this.scrollIndex + row;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private int rowYFor(RespawnEntryView entry) {
        List<RespawnEntryView> entries = filteredEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() == entry.id()) {
                return rowsTop() + (i - this.scrollIndex) * 25;
            }
        }
        return rowsTop();
    }

    private void respawnAtSelected() {
        PickYourBedClient.find(this.selectedId)
            .filter(RespawnEntryView::valid)
            .ifPresent(PickYourBedClient::selectForRespawn);
        updateSelectedButton();
    }

    private void updateSelectedButton() {
        if (this.selectedRespawnButton == null) {
            return;
        }

        boolean canRespawn = !this.hardcore
            && !PickYourBedClient.waitingForSelection()
            && PickYourBedClient.find(this.selectedId).filter(RespawnEntryView::valid).isPresent();
        this.selectedRespawnButton.active = this.delayTicker >= 20 && canRespawn;
    }

    private void setButtonsActive(boolean active) {
        for (Button button : this.exitButtons) {
            button.active = active;
        }
        updateSelectedButton();
    }

    private List<RespawnEntryView> filteredEntries() {
        return PickYourBedClient.entries().stream()
            .filter(entry -> this.filter.accepts(entry))
            .toList();
    }

    private boolean insideList(int mouseX, int mouseY) {
        return mouseX >= panelLeft() + 12 && mouseX <= panelLeft() + panelWidth() - 12 && mouseY >= rowsTop() && mouseY <= listTop() + panelHeight() - 12;
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(420, Math.max(260, this.width - 48));
    }

    private int listTop() {
        return Math.max(98, this.height / 2 - 70);
    }

    private int panelHeight() {
        return Math.min(188, Math.max(128, this.height - listTop() - 94));
    }

    private int rowsTop() {
        return listTop() + 52;
    }

    private int visibleRows() {
        return Math.max(2, (panelHeight() - 64) / 25);
    }

    private Style getClickedComponentStyleAt(int mouseX) {
        if (this.causeOfDeath == null) {
            return null;
        }
        int width = this.minecraft.font.width(this.causeOfDeath);
        int left = this.width / 2 - width / 2;
        int right = this.width / 2 + width / 2;
        return mouseX >= left && mouseX <= right ? this.minecraft.font.getSplitter().componentStyleAtWidth(this.causeOfDeath, mouseX - left) : null;
    }

    private void handleExitToTitleScreen() {
        if (this.hardcore) {
            this.exitToTitleScreen();
        } else {
            ConfirmScreen confirm = new TitleConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        this.exitToTitleScreen();
                    } else {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.respawn();
                        }
                        this.minecraft.setScreen(null);
                    }
                },
                Component.translatable("deathScreen.quit.confirm"),
                CommonComponents.EMPTY,
                Component.translatable("deathScreen.titleScreen"),
                Component.translatable("deathScreen.respawn")
            );
            this.minecraft.setScreen(confirm);
            confirm.setDelay(20);
        }
    }

    private void exitToTitleScreen() {
        if (this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }
        this.minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        this.minecraft.setScreen(new TitleScreen());
    }

    private enum RespawnFilter {
        ALL("All", 44) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return true;
            }
        },
        BEDS("Beds", 50) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return entry.type() == RespawnEntryType.BED;
            }
        },
        OTHER("Other", 58) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return entry.type().isOtherRespawn();
            }
        };

        final String label;
        final int width;

        RespawnFilter(String label, int width) {
            this.label = label;
            this.width = width;
        }

        abstract boolean accepts(RespawnEntryView entry);
    }

    private class TitleConfirmScreen extends ConfirmScreen {
        TitleConfirmScreen(BooleanConsumer callback, Component title, Component message, Component yesButton, Component noButton) {
            super(callback, title, message, yesButton, noButton);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            PickYourBedDeathScreen.this.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }
}
