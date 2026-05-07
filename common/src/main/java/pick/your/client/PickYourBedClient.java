package pick.your.client;

import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.platform.Services;
import pick.your.respawn.RespawnEntry;
import pick.your.respawn.RespawnEntryView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PickYourBedClient {
    private static List<RespawnEntryView> entries = List.of();
    private static boolean waitingForSelection;

    private PickYourBedClient() {
    }

    public static List<RespawnEntryView> entries() {
        return entries;
    }

    public static Optional<RespawnEntryView> find(long id) {
        return entries.stream().filter(entry -> entry.id() == id).findFirst();
    }

    public static void requestEntries() {
        if (Minecraft.getInstance().getConnection() != null) {
            Services.PLATFORM.sendToServer(new BedListRequestPayload());
        }
    }

    public static void handleList(BedListPayload payload) {
        entries = List.copyOf(payload.entries());
    }

    public static void handleOpenEditor(OpenEditorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        mergeEntry(payload.entry());
        minecraft.setScreen(new BedNameEditScreen(minecraft.screen, payload.entry()));
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
        Services.PLATFORM.sendToServer(new RenameRespawnPayload(id, cleanName));
    }

    public static void selectForRespawn(RespawnEntryView entry) {
        if (!entry.valid()) {
            return;
        }

        waitingForSelection = true;
        Services.PLATFORM.sendToServer(new SelectRespawnPayload(entry.id()));
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
}
