package pick.your.client;

import pick.your.respawn.ModCompatibility;
import pick.your.respawn.RespawnEntryType;
import pick.your.respawn.RespawnEntryView;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class PickYourBedDeathScreen extends Screen {
    private static final int BACKGROUND_TOP = 0x38000000;
    private static final int BACKGROUND_BOTTOM = 0x68000000;
    private static final int PANEL_COLOR = 0xA81B2027;
    private static final int PANEL_HEADER = 0x8022272F;
    private static final int SELECTED = 0x666F8190;
    private static final int ROW = 0x3A000000;
    private static final int ROW_HOVER = 0x4CFFFFFF;
    private static final int INVALID_ROW = 0x55000000;
    private static final int LIST_HEADER_HEIGHT = 38;
    private static final int LIST_TITLE_Y_OFFSET = LIST_HEADER_HEIGHT + 8;
    private static final int LIST_ROWS_TOP_OFFSET = LIST_HEADER_HEIGHT + 24;
    private static final int LIST_BOTTOM_PADDING = 10;
    private static final int EDIT_BUTTON_SIZE = 12;
    private static final int EDIT_BUTTON_ROW_PADDING = 5;
    private static final int EDIT_BUTTON_TOP_OFFSET = 5;
    private static final float ENTRY_TEXT_SCALE = 0.9F;
    private static final String BROKEN_OR_DESTROYED = "Broken or destroyed";
    private static final String[] HARDCORE_QUOTES = {
        "Well fought. Your next run starts wiser.",
        "You made it this far. Carry that forward.",
        "Every end teaches the next beginning.",
        "Rest, regroup, and try again.",
        "That run mattered. Build on it.",
        "You held on. Now come back stronger.",
        "Good run. The next story can go further.",
        "One more lesson, one more chance.",
        "You did well. Keep moving forward.",
        "The world won this round. You can win the next."
    };
    private static int lastHardcoreQuoteIndex = -1;

    private final Component causeOfDeath;
    private final boolean hardcore;
    private final String hardcoreQuote;
    private final List<Button> exitButtons = Lists.newArrayList();
    private Component deathScore = CommonComponents.EMPTY;
    private Button selectedRespawnButton;
    private Button moreStatsButton;
    private EditBox searchBox;
    private RespawnFilter filter = RespawnFilter.ALL;
    private String searchText = "";
    private long selectedId = -1L;
    private int delayTicker;
    private int refreshTicker;
    private int scrollIndex;
    private boolean initializedOnce;
    private boolean spectateRequested;
    private boolean distanceInKilometers;
    private long frozenVanillaPlayTicks = -1L;

    public PickYourBedDeathScreen(Component causeOfDeath, boolean hardcore) {
        super(Component.translatable(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.causeOfDeath = causeOfDeath;
        this.hardcore = hardcore;
        this.hardcoreQuote = hardcore ? nextHardcoreQuote() : "";
    }

    @Override
    protected void init() {
        boolean firstInit = !this.initializedOnce;
        if (this.initializedOnce) {
            this.delayTicker = Math.max(this.delayTicker, 20);
        } else {
            this.delayTicker = 0;
            this.initializedOnce = true;
        }
        this.refreshTicker = 0;
        this.exitButtons.clear();
        this.searchBox = null;
        this.selectedRespawnButton = null;
        this.moreStatsButton = null;
        this.clearWidgets();

        Layout layout = layout();
        if (this.hardcore) {
            this.searchText = "";
            if (firstInit) {
                PickYourBedClient.resetSurvivalStats();
            }
            HardcoreLayout hardcoreLayout = hardcoreLayout();
            this.exitButtons.add(this.addRenderableWidget(Button.builder(Component.literal("Spectate"), button -> {
                if (this.minecraft.player != null) {
                    if (this.minecraft.player.isSpectator() && !this.minecraft.player.isDeadOrDying()) {
                        this.minecraft.setScreen(null);
                    } else {
                        this.spectateRequested = true;
                        this.minecraft.player.respawn();
                    }
                }
                button.active = false;
            }).bounds(hardcoreLayout.buttonX, hardcoreLayout.spectateY, hardcoreLayout.buttonWidth, 20).build()));

            this.exitButtons.add(this.addRenderableWidget(Button.builder(buttonLabel("Exit to Main Menu", "Exit", hardcoreLayout.buttonWidth), button -> handleExitToTitleScreen())
                .bounds(hardcoreLayout.buttonX, hardcoreLayout.exitY, hardcoreLayout.buttonWidth, 20)
                .build()));
            if (hardcoreLayout.showMoreButton) {
                this.moreStatsButton = this.addRenderableWidget(Button.builder(Component.literal("More"), button -> openVanillaStatsScreen())
                    .bounds(hardcoreLayout.moreButtonX, hardcoreLayout.moreButtonY, hardcoreLayout.moreButtonWidth, 16)
                    .build());
                this.exitButtons.add(this.moreStatsButton);
            }
            PickYourBedClient.requestSurvivalStats();
        } else {
            PickYourBedClient.requestEntries();
            HeaderControls header = headerControls(layout);
            if (header.searchWidth >= 36) {
                this.searchBox = new EditBox(this.font, header.searchX, header.y, header.searchWidth, 18, Component.literal("Search"));
                this.searchBox.setMaxLength(32);
                this.searchBox.setValue(this.searchText);
                this.searchBox.setHint(Component.literal("Search"));
                this.searchBox.setResponder(value -> {
                    this.searchText = value;
                    this.scrollIndex = 0;
                    updateSelectedButton();
                });
                this.addRenderableWidget(this.searchBox);
            } else if (!this.searchText.isEmpty()) {
                this.searchText = "";
            }

            int filterX = header.filterX;
            RespawnFilter[] filters = RespawnFilter.values();
            for (int i = 0; i < filters.length; i++) {
                RespawnFilter value = filters[i];
                this.addRenderableWidget(Button.builder(Component.literal(header.filterLabels[i]), button -> {
                    this.filter = value;
                    this.scrollIndex = 0;
                    updateSelectedButton();
                }).bounds(filterX, header.y, header.filterWidths[i], 18).build());
                filterX += header.filterWidths[i] + header.filterGap;
            }

            int buttonY = layout.buttonTop;
            this.selectedRespawnButton = this.addRenderableWidget(Button.builder(buttonLabel("Respawn at Selected", "Selected", layout.primaryButtonWidth), button -> respawnAtSelected())
                .bounds(layout.primaryButtonX, buttonY, layout.primaryButtonWidth, 20)
                .build());
            this.exitButtons.add(this.selectedRespawnButton);

            this.exitButtons.add(this.addRenderableWidget(Button.builder(buttonLabel("Respawn at Last Point", "Last Point", layout.secondaryButtonWidth), button -> {
                PickYourBedClient.selectLastRespawnForRespawn();
                button.active = false;
                updateSelectedButton();
            }).bounds(layout.secondaryButtonX, layout.secondaryButtonY, layout.secondaryButtonWidth, 20).build()));

            this.exitButtons.add(this.addRenderableWidget(Button.builder(buttonLabel("Respawn at World Spawn", "World Spawn", layout.worldSpawnButtonWidth), button -> {
                PickYourBedClient.selectWorldSpawnForRespawn();
                button.active = false;
                updateSelectedButton();
            }).bounds(layout.worldSpawnButtonX, layout.worldSpawnButtonY, layout.worldSpawnButtonWidth, 20).build()));

            this.exitButtons.add(this.addRenderableWidget(Button.builder(buttonLabel("Title Screen", "Title", layout.titleButtonWidth), button -> handleExitToTitleScreen())
                .bounds(layout.titleButtonX, layout.titleButtonY, layout.titleButtonWidth, 20)
                .build()));
        }

        setButtonsActive(this.delayTicker >= 20);
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
            if (this.hardcore) {
                PickYourBedClient.requestSurvivalStats();
            } else {
                PickYourBedClient.requestEntries();
            }
        }
        if (this.hardcore && this.spectateRequested && this.minecraft.player != null && this.minecraft.player.isSpectator() && !this.minecraft.player.isDeadOrDying()) {
            this.minecraft.setScreen(null);
            return;
        }
        updateSelectedButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();

        if (this.hardcore) {
            HardcoreLayout hardcoreLayout = hardcoreLayout();
            if (hardcoreLayout.showDeathTitle) {
                graphics.pose().pushPose();
                graphics.pose().scale(2.0F, 2.0F, 2.0F);
                graphics.drawCenteredString(this.font, this.title, this.width / 4, hardcoreLayout.titleY / 2, 0xFFFFFFFF);
                graphics.pose().popPose();
            }

            if (hardcoreLayout.showCause && this.causeOfDeath != null) {
                graphics.drawCenteredString(this.font, this.causeOfDeath, this.width / 2, hardcoreLayout.causeY, 0xFFE8EDF2);
            }
            graphics.drawCenteredString(this.font, Component.literal("Hardcore Mode"), this.width / 2, hardcoreLayout.hardcoreY, 0xFFFF5555);
            renderHardcoreInfo(graphics, hardcoreLayout);

            renderWidgets(graphics, mouseX, mouseY, partialTick);
            if (hardcoreLayout.showCause && this.causeOfDeath != null && mouseY > hardcoreLayout.causeY && mouseY < hardcoreLayout.causeY + 9) {
                Style style = this.getClickedComponentStyleAt(mouseX);
                graphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
            }
            return;
        }

        if (layout.showDeathTitle) {
            graphics.pose().pushPose();
            graphics.pose().scale(2.0F, 2.0F, 2.0F);
            graphics.drawCenteredString(this.font, this.title, this.width / 4, layout.titleY / 2, 0xFFFFFFFF);
            graphics.pose().popPose();
        }

        if (layout.showCause && this.causeOfDeath != null) {
            graphics.drawCenteredString(this.font, this.causeOfDeath, this.width / 2, layout.causeY, 0xFFE8EDF2);
        }
        if (layout.showScore) {
            graphics.drawCenteredString(this.font, this.deathScore, this.width / 2, layout.scoreY, 0xFFE8EDF2);
        }

        RespawnEntryView tooltipEntry = renderList(graphics, mouseX, mouseY, layout);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderEntryTooltip(graphics, tooltipEntry, mouseX, mouseY);

        if (layout.showCause && this.causeOfDeath != null && mouseY > layout.causeY && mouseY < layout.causeY + 9) {
            Style style = this.getClickedComponentStyleAt(mouseX);
            graphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PickYourBedUiTextures.renderCreateWorldBackground(graphics, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hardcore) {
            HardcoreLayout layout = hardcoreLayout();
            if (layout.showCause && this.causeOfDeath != null && mouseY > layout.causeY && mouseY < layout.causeY + 9) {
                Style style = this.getClickedComponentStyleAt((int)mouseX);
                if (style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                    this.handleComponentClicked(style);
                    return false;
                }
            }
            if (insideDistanceToggle((int)mouseX, (int)mouseY, layout)) {
                this.distanceInKilometers = !this.distanceInKilometers;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Layout layout = layout();
        if (layout.showCause && this.causeOfDeath != null && mouseY > layout.causeY && mouseY < layout.causeY + 9) {
            Style style = this.getClickedComponentStyleAt((int)mouseX);
            if (style != null && style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                this.handleComponentClicked(style);
                return false;
            }
        }

        RespawnEntryView hovered = hoveredEntry((int)mouseX, (int)mouseY);
        if (hovered != null && hovered.valid()) {
            int rowY = rowYFor(hovered);
            if (insideEditButton((int)mouseX, (int)mouseY, layout, rowY)) {
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
        if (this.hardcore) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (insideList((int)mouseX, (int)mouseY)) {
            int max = Math.max(0, filteredEntries().size() - layout().visibleRows);
            this.scrollIndex = Math.max(0, Math.min(max, this.scrollIndex - (int)Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private RespawnEntryView renderList(GuiGraphics graphics, int mouseX, int mouseY, Layout layout) {
        int left = layout.panelLeft;
        int top = layout.panelTop;
        int width = layout.panelWidth;
        int height = layout.panelHeight;
        PickYourBedUiTextures.renderPanel(graphics, left, top, width, height, PANEL_COLOR);
        PickYourBedUiTextures.renderPanelHeader(graphics, left + 1, top + 1, width - 2, LIST_HEADER_HEIGHT, PANEL_HEADER);

        graphics.drawString(this.font, "Choose respawn point", left + 14, top + LIST_TITLE_Y_OFFSET, 0xFFFFFFFF, false);

        List<RespawnEntryView> entries = filteredEntries();
        if (entries.isEmpty()) {
            String message = searchQuery().isEmpty()
                ? this.filter == RespawnFilter.BEDS ? "No beds recorded" : this.filter == RespawnFilter.OTHER ? "No other respawns recorded" : "No respawns recorded"
                : "No respawns match search";
            graphics.drawCenteredString(this.font, Component.literal(message), left + width / 2, top + height / 2, 0xFF8F99A3);
            return null;
        }

        int visible = layout.visibleRows;
        if (visible <= 0) {
            graphics.drawCenteredString(this.font, Component.literal("Window too small"), left + width / 2, top + height / 2, 0xFF8F99A3);
            return null;
        }

        int max = Math.max(0, entries.size() - visible);
        this.scrollIndex = Math.max(0, Math.min(max, this.scrollIndex));
        int startY = layout.rowsTop;
        RespawnEntryView tooltipEntry = null;
        for (int i = 0; i < visible && i + this.scrollIndex < entries.size(); i++) {
            RespawnEntryView entry = entries.get(i + this.scrollIndex);
            int rowY = startY + i * 25;
            boolean rowBoundsHovered = mouseX >= left + 12 && mouseX <= left + width - 12 && mouseY >= rowY && mouseY <= rowY + 22;
            boolean editHovered = entry.valid() && insideEditButton(mouseX, mouseY, layout, rowY);
            boolean hovered = rowBoundsHovered && !editHovered;
            boolean selected = entry.id() == this.selectedId;
            int rowColor = entry.valid() ? (selected ? SELECTED : hovered ? ROW_HOVER : ROW) : INVALID_ROW;
            PickYourBedUiTextures.renderListRow(graphics, left + 12, rowY, width - 24, 22, rowColor);
            graphics.fill(left + 12, rowY, left + 15, rowY + 22, entry.type() == RespawnEntryType.BED ? 0xFFE05B65 : 0xFFB48AF1);

            int textColor = entry.valid() ? 0xFFF2F5F7 : 0xFF848B92;
            int subColor = entry.valid() ? 0xFFAAB5BF : 0xFF6F767D;
            int textMaxWidth = width - 74;
            int textBlockTop = rowY + Math.max(3, (22 - (this.font.lineHeight * 2 + 1)) / 2 + 2);
            int scaledTextMaxWidth = Math.max(1, Math.round(textMaxWidth / ENTRY_TEXT_SCALE));
            drawScaledString(graphics, trimToWidth(entry.name(), scaledTextMaxWidth), left + 22, textBlockTop, textColor, ENTRY_TEXT_SCALE);
            String subtitle = entry.type().displayName().getString() + " - " + entry.dimensionText();
            if (!entry.valid()) {
                subtitle = entry.invalidReason();
            }
            drawScaledString(graphics, trimToWidth(subtitle, scaledTextMaxWidth), left + 22, textBlockTop + this.font.lineHeight + 1, subColor, ENTRY_TEXT_SCALE);

            if (entry.valid()) {
                PickYourBedUiTextures.renderEditIcon(graphics, editButtonLeft(layout), rowY + EDIT_BUTTON_TOP_OFFSET, editHovered);
            } else {
                drawWarning(graphics, left + width - 28, rowY + 5);
            }

            if (hovered) {
                tooltipEntry = entry;
            }
        }

        if (entries.size() > visible) {
            int barTop = layout.rowsTop;
            int barHeight = visible * 25 - 3;
            int thumbHeight = Math.max(14, barHeight * visible / entries.size());
            int thumbTop = barTop + (barHeight - thumbHeight) * this.scrollIndex / Math.max(1, max);
            graphics.fill(left + width - 7, barTop, left + width - 5, barTop + barHeight, 0x66333A42);
            graphics.fill(left + width - 8, thumbTop, left + width - 4, thumbTop + thumbHeight, 0xFF80C7D4);
        }

        return tooltipEntry;
    }

    private void renderHardcoreInfo(GuiGraphics graphics, HardcoreLayout layout) {
        if (!layout.showInfoPanel) {
            return;
        }

        HardcoreStats stats = hardcoreStats();
        int left = layout.infoLeft;
        int top = layout.infoTop;
        int width = layout.infoWidth;
        int height = layout.infoHeight;
        PickYourBedUiTextures.renderPanel(graphics, left, top, width, height, 0xA620242B);
        PickYourBedUiTextures.renderPanelHeader(graphics, left + 1, top + 1, width - 2, 18, 0x8A2F1218);
        graphics.fill(left, top, left + width, top + 1, 0xCCB24A55);

        graphics.drawString(this.font, "Final Record", left + 12, top + 6, 0xFFFFE1E1, false);
        String daysLabel = width >= 190 ? "Minecraft days" : "Days";
        String timeLabel = width >= 190 ? "Play time" : "Time";
        drawHardcoreStatRow(graphics, daysLabel, stats.minecraftDays(), left, width, top + 25, 0xFFABB4BF, 0xFFFFFFFF);
        drawHardcoreStatRow(graphics, timeLabel, stats.playTime(), left, width, top + 37, 0xFFABB4BF, 0xFFFFFFFF);

        if (layout.showExtendedStats) {
            String placedLabel = width >= 200 ? "Blocks placed" : "Placed";
            String brokenLabel = width >= 200 ? "Blocks broken" : "Broken";
            String distanceLabel = this.distanceInKilometers ? "Distance (km)" : "Distance (m)";
            drawHardcoreStatRow(graphics, placedLabel, stats.blocksPlaced(), left, width, top + 49, 0xFFABB4BF, 0xFFFFFFFF);
            drawHardcoreStatRow(graphics, brokenLabel, stats.blocksBroken(), left, width, top + 61, 0xFFABB4BF, 0xFFFFFFFF);
            drawHardcoreStatRow(graphics, distanceLabel, stats.distance(), left, width, top + 73, 0xFFFFD36A, 0xFFFFF2C2);
        }

        if (layout.showQuote) {
            graphics.drawCenteredString(this.font, Component.literal(trimToWidth("\"" + this.hardcoreQuote + "\"", width - 24)), left + width / 2, top + height - 17, 0xFFFFB6BE);
        }
    }

    private void drawHardcoreStatRow(GuiGraphics graphics, String label, String value, int left, int width, int y, int labelColor, int valueColor) {
        int labelX = left + 12;
        int right = left + width - 12;
        String trimmedLabel = trimToWidth(label, Math.max(28, width / 2));
        int valueWidth = Math.max(24, right - labelX - this.font.width(trimmedLabel) - 8);
        String trimmedValue = trimToWidth(value, valueWidth);
        graphics.drawString(this.font, trimmedLabel, labelX, y, labelColor, false);
        graphics.drawString(this.font, trimmedValue, right - this.font.width(trimmedValue), y, valueColor, false);
    }

    private static String nextHardcoreQuote() {
        int index = ThreadLocalRandom.current().nextInt(HARDCORE_QUOTES.length);
        if (HARDCORE_QUOTES.length > 1 && index == lastHardcoreQuoteIndex) {
            index = (index + 1) % HARDCORE_QUOTES.length;
        }
        lastHardcoreQuoteIndex = index;
        return HARDCORE_QUOTES[index];
    }

    private void drawWarning(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 6, y + 1, x + 9, y + 3, 0xFFFFD45C);
        graphics.fill(x + 5, y + 3, x + 10, y + 5, 0xFFFFD45C);
        graphics.fill(x + 4, y + 5, x + 11, y + 7, 0xFFFFD45C);
        graphics.fill(x + 3, y + 7, x + 12, y + 10, 0xFFFFD45C);
        graphics.fill(x + 6, y + 4, x + 8, y + 7, 0xFF5F4A1D);
        graphics.fill(x + 6, y + 8, x + 8, y + 9, 0xFF5F4A1D);
    }

    private void drawScaledString(GuiGraphics graphics, String text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderEntryTooltip(GuiGraphics graphics, RespawnEntryView tooltipEntry, int mouseX, int mouseY) {
        if (tooltipEntry == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(tooltipEntry.name()));
        lines.add(Component.literal(tooltipEntry.type().displayName().getString()));
        lines.add(Component.literal("XYZ: " + tooltipEntry.coordinateText()));
        lines.add(Component.literal("Dimension: " + tooltipEntry.dimensionText()));
        lines.add(Component.literal(tooltipEntry.valid() ? "Ready to respawn" : tooltipEntry.invalidReason()));
        if (BROKEN_OR_DESTROYED.equals(tooltipEntry.invalidReason()) || ModCompatibility.shouldRemoveAfterRespawn(tooltipEntry.invalidReason())) {
            lines.add(Component.literal("Will be removed after you respawn"));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
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

    private RespawnEntryView hoveredEntry(int mouseX, int mouseY) {
        Layout layout = layout();
        if (!insideList(mouseX, mouseY)) {
            return null;
        }
        List<RespawnEntryView> entries = filteredEntries();
        int row = (mouseY - layout.rowsTop) / 25;
        if (row < 0 || row >= layout.visibleRows) {
            return null;
        }
        int index = this.scrollIndex + row;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private int rowYFor(RespawnEntryView entry) {
        Layout layout = layout();
        List<RespawnEntryView> entries = filteredEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() == entry.id()) {
                return layout.rowsTop + (i - this.scrollIndex) * 25;
            }
        }
        return layout.rowsTop;
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

        boolean selectionVisible = filteredEntries().stream().anyMatch(entry -> entry.id() == this.selectedId);
        boolean canRespawn = !this.hardcore
            && !PickYourBedClient.waitingForSelection()
            && selectionVisible
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
        String query = searchQuery();
        return PickYourBedClient.entries().stream()
            .filter(entry -> this.filter.accepts(entry))
            .filter(entry -> query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query))
            .toList();
    }

    private String searchQuery() {
        return this.searchText == null ? "" : this.searchText.trim().toLowerCase(Locale.ROOT);
    }

    private Component buttonLabel(String full, String compact, int width) {
        return Component.literal(this.font.width(full) + 8 <= width ? full : compact);
    }

    private HardcoreStats hardcoreStats() {
        PickYourBedClient.SurvivalStatsSnapshot snapshot = PickYourBedClient.survivalStats();
        long playTicks = snapshot.useServerStats() ? snapshot.playTicks() : frozenVanillaPlayTicks();
        return new HardcoreStats(
            formatMinecraftDays(playTicks),
            formatPlayTime(playTicks),
            formatCount(snapshot.blocksPlaced()),
            formatCount(snapshot.blocksBroken()),
            formatDistance(snapshot.distanceCm())
        );
    }

    private long frozenVanillaPlayTicks() {
        if (this.frozenVanillaPlayTicks < 0L) {
            this.frozenVanillaPlayTicks = vanillaDeathPlayTicks();
        }
        return this.frozenVanillaPlayTicks;
    }

    private long vanillaDeathPlayTicks() {
        long playTicks = 0L;
        if (this.minecraft != null && this.minecraft.player != null) {
            playTicks = Math.max(0, this.minecraft.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME));
            int deaths = Math.max(0, this.minecraft.player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS));
            int timeSinceDeath = Math.max(0, this.minecraft.player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH));
            if (deaths > 0) {
                return Math.max(0L, playTicks - timeSinceDeath);
            }
            playTicks = Math.max(playTicks, this.minecraft.player.tickCount);
        }

        return playTicks;
    }

    private String formatMinecraftDays(long ticks) {
        double days = ticks / 24000.0D;
        return String.format(Locale.ROOT, "%.1f", days);
    }

    private String formatPlayTime(long ticks) {
        long totalSeconds = Math.max(0, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    private String formatCount(long count) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, count));
    }

    private String formatDistance(long centimeters) {
        double meters = Math.max(0L, centimeters) / 100.0D;
        if (this.distanceInKilometers) {
            return String.format(Locale.ROOT, "%.2f km", meters / 1000.0D);
        }
        if (meters >= 100.0D) {
            return String.format(Locale.ROOT, "%.0f m", meters);
        }
        return String.format(Locale.ROOT, "%.1f m", meters);
    }

    private boolean insideDistanceToggle(int mouseX, int mouseY, HardcoreLayout layout) {
        if (!layout.showExtendedStats || !layout.showInfoPanel) {
            return false;
        }

        int rowY = layout.infoTop + 73;
        return mouseX >= layout.infoLeft + 10
            && mouseX <= layout.infoLeft + layout.infoWidth - 10
            && mouseY >= rowY - 2
            && mouseY <= rowY + 10;
    }

    private boolean insideEditButton(int mouseX, int mouseY, Layout layout, int rowY) {
        int left = editButtonLeft(layout);
        int top = rowY + EDIT_BUTTON_TOP_OFFSET;
        return mouseX >= left
            && mouseX <= left + EDIT_BUTTON_SIZE
            && mouseY >= top
            && mouseY <= top + EDIT_BUTTON_SIZE;
    }

    private int editButtonLeft(Layout layout) {
        return layout.panelLeft + layout.panelWidth - 12 - EDIT_BUTTON_ROW_PADDING - EDIT_BUTTON_SIZE;
    }

    private boolean insideList(int mouseX, int mouseY) {
        Layout layout = layout();
        return mouseX >= layout.panelLeft + 12
            && mouseX <= layout.panelLeft + layout.panelWidth - 12
            && mouseY >= layout.rowsTop
            && mouseY <= layout.panelTop + layout.panelHeight - 12;
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

    private Layout layout() {
        int screenWidth = Math.max(1, this.width);
        int screenHeight = Math.max(1, this.height);
        boolean compact = screenHeight < 260 || screenWidth < 340;
        int panelWidth = clamp(screenWidth - 48, compact ? 208 : 260, 420);
        if (screenWidth < panelWidth + 16) {
            panelWidth = Math.max(120, screenWidth - 16);
        }
        int panelLeft = (screenWidth - panelWidth) / 2;

        boolean twoColumnButtons = panelWidth >= 316;
        int buttonBlockHeight = twoColumnButtons ? 44 : 92;
        int normalPanelTop = compact ? 68 : 98;
        int maxButtonTop = Math.max(0, screenHeight - buttonBlockHeight - 10);
        boolean showHeader = maxButtonTop - normalPanelTop - 10 >= 84;
        int preferredPanelTop = showHeader ? normalPanelTop : 8;
        int availableHeight = maxButtonTop - preferredPanelTop - 10;
        int panelTop = preferredPanelTop;
        int panelHeight;
        if (availableHeight >= 84) {
            panelHeight = Math.min(208, availableHeight);
        } else {
            panelTop = Math.max(8, Math.min(preferredPanelTop, maxButtonTop - 84));
            panelHeight = Math.max(52, maxButtonTop - panelTop - 10);
        }

        panelHeight = Math.max(52, Math.min(panelHeight, Math.max(52, screenHeight - panelTop - buttonBlockHeight - 20)));
        int rowsTop = panelTop + LIST_ROWS_TOP_OFFSET;
        int rowAreaHeight = panelHeight - LIST_ROWS_TOP_OFFSET - LIST_BOTTOM_PADDING;
        int visibleRows = rowAreaHeight >= 22 ? Math.max(1, (rowAreaHeight + 3) / 25) : 0;
        int buttonTop = Math.min(maxButtonTop, panelTop + panelHeight + 10);

        int primaryButtonWidth;
        int primaryButtonX;
        int secondaryButtonWidth;
        int secondaryButtonX;
        int secondaryButtonY;
        int worldSpawnButtonWidth;
        int worldSpawnButtonX;
        int worldSpawnButtonY;
        int titleButtonWidth;
        int titleButtonX;
        int titleButtonY;
        if (twoColumnButtons) {
            primaryButtonWidth = Math.min(150, Math.max(112, (panelWidth - 12) / 2));
            secondaryButtonWidth = primaryButtonWidth;
            worldSpawnButtonWidth = primaryButtonWidth;
            titleButtonWidth = secondaryButtonWidth;
            primaryButtonX = screenWidth / 2 - primaryButtonWidth - 4;
            secondaryButtonX = screenWidth / 2 + 4;
            secondaryButtonY = buttonTop;
            worldSpawnButtonX = primaryButtonX;
            worldSpawnButtonY = buttonTop + 24;
            titleButtonX = secondaryButtonX;
            titleButtonY = buttonTop + 24;
        } else {
            primaryButtonWidth = Math.min(150, Math.max(104, panelWidth - 28));
            secondaryButtonWidth = primaryButtonWidth;
            worldSpawnButtonWidth = primaryButtonWidth;
            titleButtonWidth = primaryButtonWidth;
            primaryButtonX = screenWidth / 2 - primaryButtonWidth / 2;
            secondaryButtonX = primaryButtonX;
            secondaryButtonY = buttonTop + 24;
            worldSpawnButtonX = primaryButtonX;
            worldSpawnButtonY = buttonTop + 48;
            titleButtonX = primaryButtonX;
            titleButtonY = buttonTop + 72;
        }

        int titleY = compact ? 18 : 48;
        int causeY = compact ? 42 : 70;
        int scoreY = compact ? 54 : 84;
        return new Layout(
            panelLeft,
            panelTop,
            panelWidth,
            panelHeight,
            rowsTop,
            visibleRows,
            buttonTop,
            primaryButtonX,
            primaryButtonWidth,
            secondaryButtonX,
            secondaryButtonY,
            secondaryButtonWidth,
            worldSpawnButtonX,
            worldSpawnButtonY,
            worldSpawnButtonWidth,
            titleButtonX,
            titleButtonY,
            titleButtonWidth,
            titleY,
            causeY,
            scoreY,
            showHeader,
            showHeader,
            showHeader
        );
    }

    private HardcoreLayout hardcoreLayout() {
        int screenWidth = Math.max(1, this.width);
        int screenHeight = Math.max(1, this.height);
        int edge = 8;
        int buttonWidth = clamp(screenWidth - 48, 96, 180);
        if (screenWidth < buttonWidth + 16) {
            buttonWidth = Math.max(80, screenWidth - 16);
        }
        int buttonX = (screenWidth - buttonWidth) / 2;

        boolean compact = screenHeight < 190 || screenWidth < 260;
        int smallGap = compact ? 4 : 6;
        int largeGap = compact ? 8 : 12;
        int buttonGap = 4;
        int buttonBlockHeight = 20 + buttonGap + 20;

        int infoWidth = clamp(screenWidth - 48, 154, 380);
        if (screenWidth < infoWidth + 16) {
            infoWidth = Math.max(110, screenWidth - 16);
        }
        int infoLeft = (screenWidth - infoWidth) / 2;

        boolean showDeathTitle = screenHeight >= 104 && screenWidth >= 140;
        boolean showCause = this.causeOfDeath != null && screenHeight >= 132 && screenWidth >= 160;
        boolean showInfoPanel = screenHeight >= 154 && screenWidth >= 128;
        int infoHeight = showInfoPanel ? screenHeight >= 330 ? 138 : screenHeight >= 270 ? 126 : screenHeight >= 220 ? 108 : 78 : 0;
        int stackHeight = hardcoreStackHeight(showDeathTitle, showCause, showInfoPanel, infoHeight, smallGap, largeGap, buttonBlockHeight);
        int availableHeight = Math.max(0, screenHeight - edge * 2);
        if (stackHeight > availableHeight && showCause) {
            showCause = false;
            stackHeight = hardcoreStackHeight(showDeathTitle, false, showInfoPanel, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }
        if (stackHeight > availableHeight && showInfoPanel && infoHeight > 108) {
            infoHeight = 108;
            stackHeight = hardcoreStackHeight(showDeathTitle, showCause, true, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }
        if (stackHeight > availableHeight && showInfoPanel && infoHeight > 78) {
            infoHeight = 78;
            stackHeight = hardcoreStackHeight(showDeathTitle, showCause, true, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }
        if (stackHeight > availableHeight && showInfoPanel && infoHeight > 52) {
            infoHeight = 52;
            stackHeight = hardcoreStackHeight(showDeathTitle, showCause, true, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }
        if (stackHeight > availableHeight && showDeathTitle) {
            showDeathTitle = false;
            stackHeight = hardcoreStackHeight(false, showCause, showInfoPanel, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }
        if (stackHeight > availableHeight && showInfoPanel) {
            showInfoPanel = false;
            infoHeight = 0;
            stackHeight = hardcoreStackHeight(showDeathTitle, showCause, false, infoHeight, smallGap, largeGap, buttonBlockHeight);
        }

        int topTextHeight = hardcoreTopTextHeight(showDeathTitle, showCause, smallGap);
        int stackTop;
        if (showInfoPanel) {
            int desiredInfoCenter = (int)Math.round(screenHeight / 3.0D);
            int desiredInfoTop = desiredInfoCenter - infoHeight / 2;
            stackTop = desiredInfoTop - topTextHeight - largeGap;
        } else {
            int desiredStackCenter = (int)Math.round(screenHeight * 0.40D);
            stackTop = desiredStackCenter - stackHeight / 2;
        }
        int maxStackTop = Math.max(edge, screenHeight - edge - stackHeight);
        stackTop = clamp(stackTop, edge, maxStackTop);

        int y = stackTop;
        int titleY = y;
        if (showDeathTitle) {
            y += 18 + smallGap;
        }

        int causeY = y;
        if (showCause) {
            y += 9 + smallGap;
        }

        int hardcoreY = y;
        y += 9;

        int infoTop = 0;
        if (showInfoPanel) {
            y += largeGap;
            infoTop = y;
            y += infoHeight;
        }

        y += largeGap;
        int maxButtonTop = Math.max(edge, screenHeight - edge - buttonBlockHeight);
        int spectateY = clamp(y, edge, maxButtonTop);
        int exitY = spectateY + 20 + buttonGap;
        boolean showExtendedStats = showInfoPanel && infoHeight >= 108;
        boolean showMoreButton = showInfoPanel && infoHeight >= 108 && infoWidth >= 128;
        boolean showQuote = showInfoPanel && infoHeight >= 136;
        int moreButtonWidth = showMoreButton ? Math.min(82, Math.max(58, infoWidth - 28)) : 0;
        int moreButtonX = showMoreButton ? infoLeft + (infoWidth - moreButtonWidth) / 2 : 0;
        int moreButtonY = showMoreButton ? infoTop + infoHeight - (showQuote ? 42 : 22) : 0;
        return new HardcoreLayout(
            buttonX,
            buttonWidth,
            spectateY,
            exitY,
            titleY,
            causeY,
            hardcoreY,
            infoLeft,
            infoTop,
            infoWidth,
            infoHeight,
            showDeathTitle,
            showCause,
            showInfoPanel,
            showExtendedStats,
            showMoreButton,
            moreButtonX,
            moreButtonY,
            moreButtonWidth,
            showQuote
        );
    }

    private static int hardcoreTopTextHeight(boolean showDeathTitle, boolean showCause, int smallGap) {
        int height = 9;
        if (showDeathTitle) {
            height += 18 + smallGap;
        }
        if (showCause) {
            height += 9 + smallGap;
        }
        return height;
    }

    private static int hardcoreStackHeight(
        boolean showDeathTitle,
        boolean showCause,
        boolean showInfoPanel,
        int infoHeight,
        int smallGap,
        int largeGap,
        int buttonBlockHeight
    ) {
        int height = hardcoreTopTextHeight(showDeathTitle, showCause, smallGap);
        if (showInfoPanel) {
            height += largeGap + infoHeight;
        }
        return height + largeGap + buttonBlockHeight;
    }

    private HeaderControls headerControls(Layout layout) {
        int left = layout.panelLeft + 14;
        int right = layout.panelLeft + layout.panelWidth - 14;
        int availableWidth = Math.max(0, right - left);
        int gap = filterGap(availableWidth);
        String[] filterLabels = filterLabels(availableWidth, gap);
        int targetFilterWidth = Math.max(0, availableWidth - gap - 54);
        int[] filterWidths = filterWidths(filterLabels, targetFilterWidth, gap);
        int filterWidth = totalWidth(filterWidths, gap);
        int searchWidth = availableWidth - filterWidth - gap;

        if (searchWidth < 54) {
            targetFilterWidth = Math.max(0, availableWidth - gap - 54);
            filterLabels = shortFilterLabels();
            filterWidths = filterWidths(filterLabels, targetFilterWidth, gap);
            filterWidth = totalWidth(filterWidths, gap);
            searchWidth = availableWidth - filterWidth - gap;
        }

        if (searchWidth < 36) {
            filterLabels = shortFilterLabels();
            filterWidths = filterWidths(filterLabels, availableWidth, gap);
            filterWidth = totalWidth(filterWidths, gap);
            searchWidth = 0;
        }

        int filterX = right - filterWidth;
        return new HeaderControls(left, filterX, layout.panelTop + 10, Math.max(0, searchWidth), gap, filterWidths, filterLabels);
    }

    private String[] filterLabels(int availableWidth, int gap) {
        String[] labels = new String[RespawnFilter.values().length];
        int total = gap * Math.max(0, labels.length - 1);
        for (int i = 0; i < RespawnFilter.values().length; i++) {
            RespawnFilter filter = RespawnFilter.values()[i];
            labels[i] = filter.label;
            total += Math.max(filter.width, this.font.width(filter.label) + 12);
        }
        return total + gap + 54 <= availableWidth ? labels : shortFilterLabels();
    }

    private String[] shortFilterLabels() {
        String[] labels = new String[RespawnFilter.values().length];
        for (int i = 0; i < RespawnFilter.values().length; i++) {
            labels[i] = RespawnFilter.values()[i].shortLabel;
        }
        return labels;
    }

    private int[] filterWidths(String[] labels, int availableWidth, int gap) {
        int[] widths = new int[RespawnFilter.values().length];
        int total = 0;
        for (int i = 0; i < RespawnFilter.values().length; i++) {
            widths[i] = Math.max(RespawnFilter.values()[i].width, this.font.width(labels[i]) + 12);
            total += widths[i];
        }
        total += gap * (widths.length - 1);
        if (total <= availableWidth) {
            return widths;
        }

        total = gap * (widths.length - 1);
        for (int i = 0; i < widths.length; i++) {
            widths[i] = this.font.width(labels[i]) + 8;
            total += widths[i];
        }
        if (total <= availableWidth) {
            return widths;
        }

        int equalWidth = Math.max(18, (availableWidth - gap * (widths.length - 1)) / widths.length);
        for (int i = 0; i < widths.length; i++) {
            widths[i] = equalWidth;
        }
        return widths;
    }

    private int totalWidth(int[] widths, int gap) {
        int total = gap * Math.max(0, widths.length - 1);
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    private int filterGap(int availableWidth) {
        return availableWidth >= 160 ? 4 : 2;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Layout(
        int panelLeft,
        int panelTop,
        int panelWidth,
        int panelHeight,
        int rowsTop,
        int visibleRows,
        int buttonTop,
        int primaryButtonX,
        int primaryButtonWidth,
        int secondaryButtonX,
        int secondaryButtonY,
        int secondaryButtonWidth,
        int worldSpawnButtonX,
        int worldSpawnButtonY,
        int worldSpawnButtonWidth,
        int titleButtonX,
        int titleButtonY,
        int titleButtonWidth,
        int titleY,
        int causeY,
        int scoreY,
        boolean showDeathTitle,
        boolean showCause,
        boolean showScore
    ) {
    }

    private record HeaderControls(
        int searchX,
        int filterX,
        int y,
        int searchWidth,
        int filterGap,
        int[] filterWidths,
        String[] filterLabels
    ) {
    }

    private record HardcoreLayout(
        int buttonX,
        int buttonWidth,
        int spectateY,
        int exitY,
        int titleY,
        int causeY,
        int hardcoreY,
        int infoLeft,
        int infoTop,
        int infoWidth,
        int infoHeight,
        boolean showDeathTitle,
        boolean showCause,
        boolean showInfoPanel,
        boolean showExtendedStats,
        boolean showMoreButton,
        int moreButtonX,
        int moreButtonY,
        int moreButtonWidth,
        boolean showQuote
    ) {
    }

    private record HardcoreStats(
        String minecraftDays,
        String playTime,
        String blocksPlaced,
        String blocksBroken,
        String distance
    ) {
    }

    private void openVanillaStatsScreen() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.setScreen(new DeathStatsScreen(this));
        }
    }

    private class DeathStatsScreen extends StatsScreen {
        DeathStatsScreen(Screen parent) {
            super(parent, PickYourBedDeathScreen.this.minecraft.player.getStats());
        }

        @Override
        public void onClose() {
            PickYourBedDeathScreen.this.minecraft.setScreen(PickYourBedDeathScreen.this);
        }
    }

    private void handleExitToTitleScreen() {
        ConfirmScreen confirm = new TitleConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    this.exitToTitleScreen();
                } else {
                    this.minecraft.setScreen(this);
                }
            },
            Component.literal("Exit to title screen?"),
            Component.literal("Are you sure you want to exit to the title screen?"),
            CommonComponents.GUI_YES,
            CommonComponents.GUI_NO
        );
        this.minecraft.setScreen(confirm);
        confirm.setDelay(20);
    }

    private void exitToTitleScreen() {
        if (this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }
        this.minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        this.minecraft.setScreen(new TitleScreen());
    }

    private enum RespawnFilter {
        ALL("All", "All", 44) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return true;
            }
        },
        BEDS("Beds", "Bed", 50) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return entry.type() == RespawnEntryType.BED;
            }
        },
        OTHER("Other", "Oth", 58) {
            @Override
            boolean accepts(RespawnEntryView entry) {
                return entry.type().isOtherRespawn();
            }
        };

        final String label;
        final String shortLabel;
        final int width;

        RespawnFilter(String label, String shortLabel, int width) {
            this.label = label;
            this.shortLabel = shortLabel;
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
