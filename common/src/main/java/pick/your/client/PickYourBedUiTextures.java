package pick.your.client;

import pick.your.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

final class PickYourBedUiTextures {
    private static final ResourceLocation TAB_HEADER_BACKGROUND = texture("tab_header_background.png");
    private static final ResourceLocation MENU_BACKGROUND = texture("menu_background.png");
    private static final ResourceLocation MENU_LIST_BACKGROUND = texture("menu_list_background.png");
    private static final ResourceLocation HEADER_SEPARATOR = texture("header_separator.png");

    private PickYourBedUiTextures() {
    }

    static void renderCreateWorldBackground(GuiGraphics graphics, int width, int height, int overlayTop, int overlayBottom) {
        if (width <= 0 || height <= 0) {
            return;
        }

        Screen.renderMenuBackgroundTexture(graphics, MENU_BACKGROUND, 0, 0, 0.0F, 0.0F, width, height);
        graphics.fillGradient(0, 0, width, height, overlayTop, overlayBottom);
    }

    static void renderPanel(GuiGraphics graphics, int left, int top, int width, int height, int overlayColor) {
        if (width <= 0 || height <= 0) {
            return;
        }

        Screen.renderMenuBackgroundTexture(graphics, MENU_LIST_BACKGROUND, left, top, 0.0F, 0.0F, width, height);
        graphics.fill(left, top, left + width, top + height, overlayColor);
        renderVanillaOutline(graphics, left, top, width, height);
    }

    static void renderPanelHeader(GuiGraphics graphics, int left, int top, int width, int height, int overlayColor) {
        if (width <= 0 || height <= 0) {
            return;
        }

        graphics.blit(TAB_HEADER_BACKGROUND, left, top, 0.0F, 0.0F, width, height, 16, 16);
        graphics.fill(left, top, left + width, top + height, overlayColor);
        renderHeaderSeparator(graphics, left, top + height - 2, width);
    }

    static void renderListRow(GuiGraphics graphics, int left, int top, int width, int height, int overlayColor) {
        if (width <= 0 || height <= 0) {
            return;
        }

        Screen.renderMenuBackgroundTexture(graphics, MENU_BACKGROUND, left, top, 0.0F, 0.0F, width, height);
        graphics.fill(left, top, left + width, top + height, overlayColor);
        graphics.fill(left, top, left + width, top + 1, 0x44FFFFFF);
        graphics.fill(left, top + height - 1, left + width, top + height, 0x88000000);
    }

    private static void renderHeaderSeparator(GuiGraphics graphics, int left, int top, int width) {
        graphics.blit(HEADER_SEPARATOR, left, top, 0.0F, 0.0F, width, 2, 32, 2);
    }

    private static void renderVanillaOutline(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + 1, 0xEE000000);
        graphics.fill(left, top + height - 1, left + width, top + height, 0xEE000000);
        graphics.fill(left, top, left + 1, top + height, 0xEE000000);
        graphics.fill(left + width - 1, top, left + width, top + height, 0xEE000000);
        if (width <= 2 || height <= 2) {
            return;
        }

        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, 0x77FFFFFF);
        graphics.fill(left + 1, top + 1, left + 2, top + height - 1, 0x55FFFFFF);
        graphics.fill(left + 1, top + height - 2, left + width - 1, top + height - 1, 0x99000000);
        graphics.fill(left + width - 2, top + 1, left + width - 1, top + height - 1, 0x99000000);
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/world_creation/" + fileName);
    }
}
