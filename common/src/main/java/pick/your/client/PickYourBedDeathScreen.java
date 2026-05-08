package pick.your.client;

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

public class PickYourBedDeathScreen extends Screen {
    private static final int BACKGROUND_TOP = 0x60500000;
    private static final int BACKGROUND_BOTTOM = 0xA0803030;
    private static final int PANEL_COLOR = 0xD82D343E;
    private static final int PANEL_HEADER = 0xCC343D49;
    private static final int PANEL_BORDER = 0xD067717E;
    private static final int ACCENT = 0xFF80C7D4;
    private static final int SELECTED = 0xE02F6D79;
    private static final int ROW = 0xCC3B444F;
    private static final int ROW_HOVER = 0xD947525F;
    private static final int INVALID_ROW = 0x993A3F45;
    private static final String BROKEN_OR_DESTROYED = "Broken or destroyed";
    private static final String HARDCORE_QUOTE = "\"Another story of a legend comes to an end\"";

    private final Component causeOfDeath;
    private final boolean hardcore;
    private final List<Button> exitButtons = Lists.newArrayList();
    private Component deathScore = CommonComponents.EMPTY;
    private Button selectedRespawnButton;
    private EditBox searchBox;
    private RespawnFilter filter = RespawnFilter.ALL;
    private String searchText = "";
    private long selectedId = -1L;
    private int delayTicker;
    private int refreshTicker;
    private int scrollIndex;
    private boolean initializedOnce;
    private boolean spectateRequested;

    public PickYourBedDeathScreen(Component causeOfDeath, boolean hardcore) {
        super(Component.translatable(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.causeOfDeath = causeOfDeath;
        this.hardcore = hardcore;
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
                if (this.minecraft.player != null) {
                    this.minecraft.player.respawn();
                }
                button.active = false;
            }).bounds(layout.secondaryButtonX, layout.secondaryButtonY, layout.secondaryButtonWidth, 20).build()));

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
        graphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
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
            int iconLeft = layout.panelLeft + layout.panelWidth - 30;
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
        graphics.fill(left, top, left + width, top + height, PANEL_COLOR);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 30, PANEL_HEADER);
        graphics.fill(left, top, left + width, top + 1, ACCENT);
        graphics.fill(left, top + height - 1, left + width, top + height, PANEL_BORDER);
        graphics.fill(left, top, left + 1, top + height, PANEL_BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, PANEL_BORDER);

        graphics.drawString(this.font, "Choose respawn point", left + 14, top + 36, 0xFFFFFFFF, false);

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
            boolean hovered = mouseX >= left + 12 && mouseX <= left + width - 12 && mouseY >= rowY && mouseY <= rowY + 22;
            boolean selected = entry.id() == this.selectedId;
            int rowColor = entry.valid() ? (selected ? SELECTED : hovered ? ROW_HOVER : ROW) : INVALID_ROW;
            graphics.fill(left + 12, rowY, left + width - 12, rowY + 22, rowColor);
            graphics.fill(left + 12, rowY, left + 15, rowY + 22, entry.type() == RespawnEntryType.BED ? 0xFFE05B65 : 0xFFB48AF1);

            int textColor = entry.valid() ? 0xFFF2F5F7 : 0xFF848B92;
            int subColor = entry.valid() ? 0xFFAAB5BF : 0xFF6F767D;
            int textMaxWidth = width - 74;
            graphics.drawString(this.font, trimToWidth(entry.name(), textMaxWidth), left + 22, rowY + 4, textColor, false);
            String subtitle = entry.type().displayName().getString() + " - " + entry.dimensionText();
            if (!entry.valid()) {
                subtitle = entry.invalidReason();
            }
            graphics.drawString(this.font, trimToWidth(subtitle, textMaxWidth), left + 22, rowY + 14, subColor, false);

            if (entry.valid()) {
                drawPencil(graphics, left + width - 28, rowY + 5, hovered ? 0xFFFFFFFF : 0xFFB8C2CC);
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
        graphics.fill(left, top, left + width, top + height, 0xB820242B);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 18, 0xAA2F1218);
        graphics.fill(left, top, left + width, top + 1, 0xFFFF5555);
        graphics.fill(left, top + height - 1, left + width, top + height, 0xAA883C45);
        graphics.fill(left, top, left + 1, top + height, 0xAA883C45);
        graphics.fill(left + width - 1, top, left + width, top + height, 0xAA883C45);

        graphics.drawString(this.font, "Final Record", left + 12, top + 6, 0xFFFFE1E1, false);
        String daysLabel = width >= 190 ? "Minecraft days" : "Days";
        String timeLabel = width >= 190 ? "Play time" : "Time";
        int valueWidth = Math.max(32, width - 24 - Math.max(this.font.width(daysLabel), this.font.width(timeLabel)) - 10);
        String daysValue = trimToWidth(stats.minecraftDays(), valueWidth);
        String timeValue = trimToWidth(stats.playTime(), valueWidth);
        graphics.drawString(this.font, daysLabel, left + 12, top + 25, 0xFFABB4BF, false);
        graphics.drawString(this.font, daysValue, left + width - 12 - this.font.width(daysValue), top + 25, 0xFFFFFFFF, false);
        graphics.drawString(this.font, timeLabel, left + 12, top + 37, 0xFFABB4BF, false);
        graphics.drawString(this.font, timeValue, left + width - 12 - this.font.width(timeValue), top + 37, 0xFFFFFFFF, false);

        if (layout.showQuote) {
            graphics.drawCenteredString(this.font, Component.literal(trimToWidth(HARDCORE_QUOTE, width - 24)), left + width / 2, top + height - 17, 0xFFFFB6BE);
        }
    }

    private void drawPencil(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 3, y + 9, x + 5, y + 11, 0xFFC6924E);
        graphics.fill(x + 5, y + 7, x + 7, y + 9, color);
        graphics.fill(x + 7, y + 5, x + 9, y + 7, color);
        graphics.fill(x + 9, y + 3, x + 11, y + 5, color);
        graphics.fill(x + 10, y + 2, x + 12, y + 4, 0xFFE8B86A);
    }

    private void drawWarning(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 6, y + 1, x + 9, y + 3, 0xFFFFD45C);
        graphics.fill(x + 5, y + 3, x + 10, y + 5, 0xFFFFD45C);
        graphics.fill(x + 4, y + 5, x + 11, y + 7, 0xFFFFD45C);
        graphics.fill(x + 3, y + 7, x + 12, y + 10, 0xFFFFD45C);
        graphics.fill(x + 6, y + 4, x + 8, y + 7, 0xFF5F4A1D);
        graphics.fill(x + 6, y + 8, x + 8, y + 9, 0xFF5F4A1D);
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
        if (BROKEN_OR_DESTROYED.equals(tooltipEntry.invalidReason())) {
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
        long playTicks = snapshot.useModStats() ? snapshot.playTicks() : vanillaPlayTicks();
        return new HardcoreStats(formatMinecraftDays(playTicks), formatPlayTime(playTicks));
    }

    private long vanillaPlayTicks() {
        long playTicks = 0L;
        if (this.minecraft != null && this.minecraft.player != null) {
            playTicks = Math.max(0, this.minecraft.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME));
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
        int buttonBlockHeight = twoColumnButtons ? 44 : 68;
        int normalPanelTop = compact ? 68 : 98;
        int maxButtonTop = Math.max(0, screenHeight - buttonBlockHeight - 10);
        boolean showHeader = maxButtonTop - normalPanelTop - 10 >= 84;
        int preferredPanelTop = showHeader ? normalPanelTop : 8;
        int availableHeight = maxButtonTop - preferredPanelTop - 10;
        int panelTop = preferredPanelTop;
        int panelHeight;
        if (availableHeight >= 84) {
            panelHeight = Math.min(188, availableHeight);
        } else {
            panelTop = Math.max(8, Math.min(preferredPanelTop, maxButtonTop - 84));
            panelHeight = Math.max(52, maxButtonTop - panelTop - 10);
        }

        panelHeight = Math.max(52, Math.min(panelHeight, Math.max(52, screenHeight - panelTop - buttonBlockHeight - 20)));
        int rowsTop = panelTop + 52;
        int rowAreaHeight = panelHeight - 62;
        int visibleRows = rowAreaHeight >= 22 ? Math.max(1, (rowAreaHeight + 3) / 25) : 0;
        int buttonTop = Math.min(maxButtonTop, panelTop + panelHeight + 10);

        int primaryButtonWidth;
        int primaryButtonX;
        int secondaryButtonWidth;
        int secondaryButtonX;
        int secondaryButtonY;
        int titleButtonWidth;
        int titleButtonX;
        int titleButtonY;
        if (twoColumnButtons) {
            primaryButtonWidth = Math.min(150, Math.max(112, (panelWidth - 12) / 2));
            secondaryButtonWidth = primaryButtonWidth;
            primaryButtonX = screenWidth / 2 - primaryButtonWidth - 4;
            secondaryButtonX = screenWidth / 2 + 4;
            secondaryButtonY = buttonTop;
            titleButtonWidth = Math.min(150, panelWidth - 28);
            titleButtonX = screenWidth / 2 - titleButtonWidth / 2;
            titleButtonY = buttonTop + 24;
        } else {
            primaryButtonWidth = Math.min(150, Math.max(104, panelWidth - 28));
            secondaryButtonWidth = primaryButtonWidth;
            primaryButtonX = screenWidth / 2 - primaryButtonWidth / 2;
            secondaryButtonX = primaryButtonX;
            secondaryButtonY = buttonTop + 24;
            titleButtonWidth = primaryButtonWidth;
            titleButtonX = primaryButtonX;
            titleButtonY = buttonTop + 48;
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
        int buttonWidth = clamp(screenWidth - 48, 96, 180);
        if (screenWidth < buttonWidth + 16) {
            buttonWidth = Math.max(80, screenWidth - 16);
        }

        int buttonX = (screenWidth - buttonWidth) / 2;
        int exitY = Math.max(8, screenHeight - 34);
        int spectateY = Math.max(8, exitY - 24);
        int textBottom = spectateY - 8;
        boolean compact = screenHeight < 190 || screenWidth < 260;
        int titleY = compact ? 16 : 36;
        int causeY = compact ? 38 : 62;
        boolean showDeathTitle = textBottom >= titleY + 18 && screenHeight >= 104;
        if (!showDeathTitle) {
            causeY = 14;
        }

        boolean showCause = textBottom >= causeY + 9;
        int hardcoreY = showCause && this.causeOfDeath != null ? causeY + 14 : causeY;
        if (hardcoreY > textBottom - 9) {
            hardcoreY = Math.max(8, textBottom - 9);
            showCause = false;
        }

        int infoWidth = clamp(screenWidth - 48, 154, 340);
        if (screenWidth < infoWidth + 16) {
            infoWidth = Math.max(110, screenWidth - 16);
        }
        int infoLeft = (screenWidth - infoWidth) / 2;
        int infoAreaTop = hardcoreY + 16;
        int infoAreaBottom = spectateY - 10;
        int infoAvailable = infoAreaBottom - infoAreaTop;
        boolean showInfoPanel = infoAvailable >= 52;
        int infoHeight = showInfoPanel ? Math.min(78, Math.max(52, infoAvailable)) : 0;
        int infoTop = showInfoPanel ? infoAreaTop + Math.max(0, (infoAvailable - infoHeight) / 2) : 0;
        boolean showQuote = infoHeight >= 68;
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
            showQuote
        );
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
        return new HeaderControls(left, filterX, layout.panelTop + 12, Math.max(0, searchWidth), gap, filterWidths, filterLabels);
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
        boolean showQuote
    ) {
    }

    private record HardcoreStats(
        String minecraftDays,
        String playTime
    ) {
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
