package pick.your.client;

import pick.your.Constants;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.network.payload.SurvivalStatsPayload;
import pick.your.network.payload.SurvivalStatsRequestPayload;
import pick.your.platform.Services;
import pick.your.respawn.RespawnEntry;
import pick.your.respawn.RespawnEntryView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PickYourBedClient {
    private static List<RespawnEntryView> entries = List.of();
    private static boolean waitingForSelection;
    private static SurvivalStatsSnapshot survivalStats = SurvivalStatsSnapshot.vanilla();

    private PickYourBedClient() {
    }

    public static List<RespawnEntryView> entries() {
        return entries;
    }

    public static Optional<RespawnEntryView> find(long id) {
        return entries.stream().filter(entry -> entry.id() == id).findFirst();
    }

    public static void requestEntries() {
        sendToServer("list request", new BedListRequestPayload());
    }

    public static void requestSurvivalStats() {
        sendToServer("survival stats request", new SurvivalStatsRequestPayload());
    }

    public static void handleList(BedListPayload payload) {
        entries = List.copyOf(payload.entries());
    }

    public static void handleOpenEditor(OpenEditorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }

        mergeEntry(payload.entry());
        Screen parent = minecraft.screen instanceof PickYourBedDeathScreen ? minecraft.screen : null;
        minecraft.setScreen(new BedNameEditScreen(parent, payload.entry()));
    }

    public static void rename(long id, String name) {
        String cleanName = RespawnEntry.sanitizeName(name, "Bed");
        List<RespawnEntryView> next = new ArrayList<>(entries.size());
        for (RespawnEntryView entry : entries) {
            next.add(entry.id() == id
                ? new RespawnEntryView(entry.id(), entry.type(), entry.dimension(), entry.pos(), cleanName, entry.valid(), entry.invalidReason())
                : entry);
        }
        entries = List.copyOf(next);
        sendToServer("rename", new RenameRespawnPayload(id, cleanName));
    }

    public static void selectForRespawn(RespawnEntryView entry) {
        if (!entry.valid()) {
            return;
        }

        waitingForSelection = true;
        if (!sendToServer("select", new SelectRespawnPayload(entry.id()))) {
            waitingForSelection = false;
        }
    }

    public static void handleSelectionResult(SelectionResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.success()) {
            waitingForSelection = false;
            if (minecraft.player != null) {
                minecraft.player.respawn();
            }
            return;
        }

        waitingForSelection = false;
        if (!payload.message().isBlank() && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(payload.message()), true);
        }
    }

    public static void handleSurvivalStats(SurvivalStatsPayload payload) {
        survivalStats = new SurvivalStatsSnapshot(payload.useServerStats(), Math.max(0L, payload.playTicks()));
    }

    public static void resetSurvivalStats() {
        survivalStats = SurvivalStatsSnapshot.vanilla();
    }

    public static SurvivalStatsSnapshot survivalStats() {
        return survivalStats;
    }

    public static boolean waitingForSelection() {
        return waitingForSelection;
    }

    private static void mergeEntry(RespawnEntryView updated) {
        List<RespawnEntryView> next = new ArrayList<>(entries);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id() == updated.id()) {
                next.set(i, updated);
                entries = List.copyOf(next);
                return;
            }
        }
        next.add(0, updated);
        entries = List.copyOf(next);
    }

    private static boolean sendToServer(String action, CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() == null) {
            return false;
        }

        try {
            Services.PLATFORM.sendToServer(payload);
            return true;
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to send {} packet to the server", action, exception);
            return false;
        }
    }

    public record SurvivalStatsSnapshot(boolean useServerStats, long playTicks) {
        static SurvivalStatsSnapshot vanilla() {
            return new SurvivalStatsSnapshot(false, 0L);
        }
    }
}
