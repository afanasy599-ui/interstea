package dev.afanasy.interstellar.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Cinematic Tesseract scene. The old 3x3 dashboard is replaced by a spatial
 * corridor of world-windows. Each window is made from a captured surface map
 * of the actual client-visible Minecraft world at a different time.
 */
public final class TesseractScreen extends Screen {
    private int selectedIndex;
    private long animationTicks;

    public TesseractScreen() {
        super(Text.literal("Interstellar Experience — Tesseract"));
        this.selectedIndex = Math.max(0, TesseractHistory.size() - 1);
    }

    @Override
    public boolean shouldPause() {
        // The world keeps moving behind the Tesseract.
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        animationTicks++;
        context.fill(0, 0, width, height, 0xFF01030A);
        drawNebula(context);
        drawStars(context);
        drawSpaceLattice(context);

        List<TesseractHistory.Snapshot> snapshots = TesseractHistory.getSnapshots();
        if (!snapshots.isEmpty()) {
            selectedIndex = Math.max(0, Math.min(selectedIndex, snapshots.size() - 1));
            drawTimeCorridor(context, snapshots);
        } else {
            drawNoWorldSignal(context);
        }

        drawGargantua(context, width - Math.max(104, width / 9), Math.max(104, height / 5),
                Math.max(36, Math.min(76, width / 18)));
        drawCinematicText(context, snapshots);
    }

    private void drawNebula(DrawContext context) {
        int cx = width / 2;
        int cy = height / 2;
        // Broad, low-contrast bands create a soft cosmic glow without shaders.
        for (int i = 0; i < 22; i++) {
            int spread = 22 + i * 13;
            int alpha = Math.max(2, 17 - i / 2);
            int color = (alpha << 24) | 0x10294B;
            context.fill(cx - spread * 2, cy - spread, cx + spread * 2, cy + spread, color);
        }
        for (int i = 0; i < 8; i++) {
            int y = cy - 130 + i * 38;
            int alpha = 8 + (int) (5 * Math.sin(animationTicks * 0.018 + i));
            context.fill(0, y, width, y + 1, (alpha << 24) | 0x537DA6);
        }
    }

    private void drawStars(DrawContext context) {
        for (int i = 0; i < 130; i++) {
            int x = Math.floorMod(i * 97 + 31, Math.max(1, width));
            int drift = (int) ((animationTicks / (3 + (i % 6))) % Math.max(1, height));
            int y = Math.floorMod(i * 53 + drift, Math.max(1, height));
            int size = (i % 13 == 0) ? 2 : 1;
            int alpha = (i % 3 == 0) ? 175 : 105;
            int color = (alpha << 24) | ((i % 4 == 0) ? 0xA9D8FF : 0x7898C5);
            context.fill(x, y, x + size, y + size, color);
        }
    }

    /** Perspective rails converge on the central time opening. */
    private void drawSpaceLattice(DrawContext context) {
        int vx = width / 2;
        int vy = height / 2;
        int rail = 0x365B85A8;
        int dimRail = 0x1B7390B8;
        int[][] corners = {
                {0, 0}, {width, 0}, {0, height}, {width, height},
                {width / 2, 0}, {width / 2, height}, {0, height / 2}, {width, height / 2}
        };
        for (int[] corner : corners) {
            drawLine(context, corner[0], corner[1], vx, vy, dimRail);
        }

        // Receding rectangular frames make the screen read as a long structure,
        // not as a conventional menu or a grid of cards.
        for (int i = 0; i < 8; i++) {
            double scale = 1.0 - i * 0.105;
            int halfW = (int) (width * 0.48 * scale);
            int halfH = (int) (height * 0.43 * scale);
            int pulse = (int) (Math.sin(animationTicks * 0.025 + i * 0.6) * 10);
            int color = (Math.max(12, 43 - i * 4) << 24) | (0x4A86B4 + Math.max(0, pulse));
            outline(context, vx - halfW, vy - halfH, vx + halfW, vy + halfH, color, i == 0 ? 2 : 1);
        }

        // Thin crossbeams run through the portals, suggesting repeated space-time slices.
        for (int i = -3; i <= 3; i++) {
            int y = vy + i * Math.max(18, height / 18);
            drawLine(context, 0, y, width, vy + i * 3, rail);
        }
    }

    private void drawTimeCorridor(DrawContext context, List<TesseractHistory.Snapshot> snapshots) {
        int count = Math.min(9, snapshots.size());
        if (count == 0) return;

        // Back/side windows are drawn first so the selected moment sits in front.
        for (int slot = 0; slot < count; slot++) {
            if (slot == selectedIndex) continue;
            Portal portal = portalFor(slot, false);
            drawWorldPortal(context, snapshots, slot, portal, false);
        }

        Portal foreground = portalFor(selectedIndex, true);
        drawWorldPortal(context, snapshots, selectedIndex, foreground, true);

        // A thin luminous axis links the different moments to the selected one.
        int cx = width / 2;
        int cy = height / 2;
        int pulse = 22 + (int) (12 * (0.5 + 0.5 * Math.sin(animationTicks * 0.05)));
        drawLine(context, 0, cy, width, cy, (pulse << 24) | 0x79B9E8);
        drawLine(context, cx, 0, cx, height, 0x1879A8D8);
    }

    private Portal portalFor(int index, boolean foreground) {
        double[][] positions = {
                {0.50, 0.51}, // central axis
                {0.14, 0.48}, {0.86, 0.48},
                {0.29, 0.18}, {0.71, 0.18},
                {0.29, 0.82}, {0.71, 0.82},
                {0.055, 0.20}, {0.945, 0.80}
        };
        double[] p = positions[index % positions.length];
        // Slow camera drift makes the windows feel suspended in a moving 5D space.
        double phase = animationTicks * 0.018 + index * 0.83;
        int driftX = (int) (Math.sin(phase) * (foreground ? 2.0 : 7.0));
        int driftY = (int) (Math.cos(phase * 0.82) * (foreground ? 2.0 : 5.0));
        int portalW;
        int portalH;
        if (foreground) {
            portalW = Math.min((int) (width * 0.48), 700);
            portalH = Math.min((int) (height * 0.62), 470);
        } else {
            portalW = Math.max(112, (int) (width * ((index >= 7) ? 0.17 : 0.205)));
            portalH = Math.max(76, (int) (height * ((index >= 7) ? 0.18 : 0.22)));
        }
        return new Portal((int) (width * p[0]) + driftX,
                (int) (height * p[1]) + driftY, portalW, portalH);
    }

    private void drawWorldPortal(DrawContext context,
                                 List<TesseractHistory.Snapshot> snapshots,
                                 int index,
                                 Portal portal,
                                 boolean foreground) {
        TesseractHistory.Snapshot snapshot = snapshots.get(index);
        int x = portal.centerX - portal.portalWidth / 2;
        int y = portal.centerY - portal.portalHeight / 2;
        int w = portal.portalWidth;
        int h = portal.portalHeight;

        // Shadow, outer glow, metal frame and dark inner aperture.
        context.fill(x - 8, y - 8, x + w + 8, y + h + 8, foreground ? 0x523F83B8 : 0x263E6790);
        context.fill(x - 4, y - 4, x + w + 4, y + h + 4, foreground ? 0xD6A8C9DF : 0xA65D7899);
        context.fill(x, y, x + w, y + h, foreground ? 0xF0040A14 : 0xD9020710);
        outline(context, x, y, x + w, y + h, foreground ? 0xFFB9D8ED : 0xA66B9BC0, foreground ? 3 : 2);
        outline(context, x + 5, y + 5, x + w - 5, y + h - 5,
                foreground ? 0xA5E7D8B0 : 0x665D91B5, 1);

        int pad = foreground ? 20 : 9;
        int innerW = Math.max(20, w - pad * 2);
        int innerH = Math.max(20, h - pad * 2 - 20);
        int cell = Math.max(1, Math.min(innerW / TesseractHistory.MAP_SIZE,
                innerH / TesseractHistory.MAP_SIZE));
        int mapW = cell * TesseractHistory.MAP_SIZE;
        int mapH = cell * TesseractHistory.MAP_SIZE;
        int mapX = x + (w - mapW) / 2;
        int mapY = y + (h - mapH) / 2 + (foreground ? 1 : -3);
        if (foreground) {
            drawVoxelWorld(context, snapshot, mapX, mapY, mapW, mapH);
        } else {
            drawTerrainMap(context, snapshot, mapX, mapY, cell, false);
        }

        String label = timeLabel(snapshots, index);
        int labelColor = foreground ? 0xFFF0E4CB : 0xFF91AFCB;
        context.drawTextWithShadow(textRenderer, label, x + 10, y + h - 15, labelColor);
        if (foreground) {
            String coordinates = "WORLD ECHO  //  " + snapshot.centerX() + "  "
                    + snapshot.centerY() + "  " + snapshot.centerZ();
            context.drawTextWithShadow(textRenderer, coordinates, x + 10, y + 10, 0xFFE2EAF4);
        }
    }

    /**
     * Draws the real recorded Minecraft surface as a diamond/isometric terrain
     * projection. Each coloured cell corresponds to a sampled surface column.
     */
    private void drawTerrainMap(DrawContext context,
                                TesseractHistory.Snapshot snapshot,
                                int x,
                                int y,
                                int cell,
                                boolean detailed) {
        int gap = Math.max(0, cell >= 5 ? 1 : 0);
        for (int mapZ = 0; mapZ < TesseractHistory.MAP_SIZE; mapZ++) {
            for (int mapX = 0; mapX < TesseractHistory.MAP_SIZE; mapX++) {
                int color = snapshot.colorAt(mapX, mapZ);
                if (color == 0) color = 0xFF111D2B;
                int heightShade = snapshot.heightAt(mapX, mapZ);
                color = shade(color, heightShade);
                int left = x + mapX * cell;
                int top = y + mapZ * cell;
                context.fill(left, top, left + cell - gap, top + cell - gap, color);
                if (detailed && cell >= 7) {
                    int edge = shade(color, -5);
                    context.fill(left, top + cell - 2, left + cell - gap, top + cell - 1, edge);
                }
            }
        }
    }


    /**
     * Draws the recorded local world volume as an isometric miniature made from
     * real sampled block colours. It is the central replacement for the film's
     * bookshelf view: a small 3D projection of the player's Minecraft terrain.
     */
    private void drawVoxelWorld(DrawContext context,
                                TesseractHistory.Snapshot snapshot,
                                int x,
                                int y,
                                int availableWidth,
                                int availableHeight) {
        int halfW = Math.max(2, Math.min(13, availableWidth / (TesseractHistory.MAP_SIZE * 2)));
        int tileW = halfW * 2;
        int halfH = Math.max(1, tileW / 4);
        int blockH = Math.max(2, (int) (tileW * 0.68));
        int extentX = (TesseractHistory.MAP_SIZE - 1) * tileW;
        int extentY = (TesseractHistory.MAP_SIZE * 2 - 2) * halfH
                + (TesseractHistory.VOXEL_HEIGHT - 1) * blockH + halfH * 2;

        // Fit the projected block volume within the portal aperture.
        double scale = Math.min(1.0,
                Math.min((availableWidth - 18.0) / Math.max(1, extentX),
                        (availableHeight - 18.0) / Math.max(1, extentY)));
        if (scale < 1.0) {
            halfW = Math.max(2, (int) (halfW * scale));
            tileW = halfW * 2;
            halfH = Math.max(1, tileW / 4);
            blockH = Math.max(2, (int) (tileW * 0.68));
            extentX = (TesseractHistory.MAP_SIZE - 1) * tileW;
            extentY = (TesseractHistory.MAP_SIZE * 2 - 2) * halfH
                    + (TesseractHistory.VOXEL_HEIGHT - 1) * blockH + halfH * 2;
        }

        int centerX = x + availableWidth / 2;
        int baseY = y + availableHeight / 2
                - ((TesseractHistory.MAP_SIZE * 2 - 2) * halfH + halfH * 2
                - (TesseractHistory.VOXEL_HEIGHT - 1) * blockH) / 2;

        // Painter's order: far columns first, then near columns; lower blocks first.
        for (int depth = 0; depth <= (TesseractHistory.MAP_SIZE - 1) * 2; depth++) {
            for (int blockX = 0; blockX < TesseractHistory.MAP_SIZE; blockX++) {
                int blockZ = depth - blockX;
                if (blockZ < 0 || blockZ >= TesseractHistory.MAP_SIZE) continue;

                int screenX = centerX + (blockX - blockZ) * halfW;
                for (int blockY = 0; blockY < TesseractHistory.VOXEL_HEIGHT; blockY++) {
                    int color = snapshot.voxelColorAt(blockX, blockY, blockZ);
                    if (color == 0) continue;

                    int screenY = baseY + (blockX + blockZ) * halfH - blockY * blockH;
                    int leftNeighbour = blockX > 0
                            ? snapshot.voxelColorAt(blockX - 1, blockY, blockZ) : 0;
                    int rightNeighbour = blockZ > 0
                            ? snapshot.voxelColorAt(blockX, blockY, blockZ - 1) : 0;
                    int above = blockY + 1 < TesseractHistory.VOXEL_HEIGHT
                            ? snapshot.voxelColorAt(blockX, blockY + 1, blockZ) : 0;

                    if (leftNeighbour == 0) {
                        context.fill(screenX - halfW, screenY + halfH / 2,
                                screenX, screenY + halfH / 2 + blockH, shade(color, -28));
                    }
                    if (rightNeighbour == 0) {
                        context.fill(screenX, screenY + halfH / 2,
                                screenX + halfW, screenY + halfH / 2 + blockH, shade(color, -42));
                    }
                    if (above == 0) {
                        drawDiamond(context, screenX, screenY, halfW, halfH, shade(color, 8));
                    }
                }
            }
        }

        // A subtle ground plane gives the miniature a clear spatial footprint.
        int edgeColor = 0x665D9AC7;
        drawLine(context, centerX - extentX / 2, baseY + extentY / 2,
                centerX, baseY + extentY / 2 + halfH, edgeColor);
        drawLine(context, centerX, baseY + extentY / 2 + halfH,
                centerX + extentX / 2, baseY + extentY / 2, edgeColor);
    }

    private void drawDiamond(DrawContext context, int centerX, int centerY,
                             int halfW, int halfH, int color) {
        for (int dy = -halfH; dy <= halfH; dy++) {
            int span = (int) (halfW * (1.0 - (double) Math.abs(dy) / Math.max(1, halfH)));
            context.fill(centerX - span, centerY + dy,
                    centerX + span + 1, centerY + dy + 1, color);
        }
    }

    private void drawNoWorldSignal(DrawContext context) {
        int cx = width / 2;
        int cy = height / 2;
        drawGargantua(context, cx, cy - 30, Math.max(52, Math.min(width, height) / 7));
        context.drawCenteredTextWithShadow(textRenderer, "NO TEMPORAL ECHO", cx, cy + 100, 0xFFE8D9B8);
        context.drawCenteredTextWithShadow(textRenderer,
                "ОЖИДАЕМ ПЕРВЫЙ ОТПЕЧАТОК ТВОЕГО МИРА", cx, cy + 116, 0xFF8EA8C4);
    }

    private void drawGargantua(DrawContext context, int centerX, int centerY, int radius) {
        int pulse = (int) (Math.sin(animationTicks * 0.045) * Math.max(2, radius / 5));
        // Accretion disc: several offset elliptical bands around the dark centre.
        for (int band = 0; band < 5; band++) {
            int r = radius + band * 4;
            for (int dy = -r / 2; dy <= r / 2; dy++) {
                double normalized = (double) dy / Math.max(1, r / 2);
                int half = (int) (r * Math.sqrt(Math.max(0.0, 1.0 - normalized * normalized)));
                int inner = (int) (half * 0.54);
                int alpha = Math.max(16, 94 - band * 14 - Math.abs(dy));
                int red = Math.max(0, Math.min(255, 215 + pulse - band * 12));
                int green = Math.max(0, Math.min(255, 126 + pulse / 2 - band * 15));
                int blue = Math.max(0, Math.min(255, 52 + band * 13));
                int color = (alpha << 24) | (red << 16) | (green << 8) | blue;
                context.fill(centerX - half, centerY + dy, centerX - inner, centerY + dy + 1, color);
                context.fill(centerX + inner, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
            }
        }
        context.fill(centerX - radius / 2, centerY - radius / 2,
                centerX + radius / 2, centerY + radius / 2, 0xFF010207);
        context.fill(centerX - radius / 2 + 2, centerY - radius / 2 + 2,
                centerX + radius / 2 - 2, centerY + radius / 2 - 2, 0xFF000104);
    }

    private void drawCinematicText(DrawContext context, List<TesseractHistory.Snapshot> snapshots) {
        context.drawTextWithShadow(textRenderer, "INTERSTELLAR", 18, 15, 0xFFE4EAF2);
        context.drawTextWithShadow(textRenderer, "THE TESSERACT  //  WORLD-TIME OBSERVATION", 18, 29, 0xFF829FBD);
        context.drawTextWithShadow(textRenderer, "GARGANTUA", width - 116, Math.max(10, height / 5 - 12), 0xFFD5B78A);

        int subtitleY = height - 62;
        context.fill(width / 2 - Math.min(360, width / 3), subtitleY - 10,
                width / 2 + Math.min(360, width / 3), subtitleY + 35, 0x99010712);
        context.drawCenteredTextWithShadow(textRenderer,
                "У них есть доступ к бесконечности времени и пространству,",
                width / 2, subtitleY, 0xFFF0E8DA);
        context.drawCenteredTextWithShadow(textRenderer,
                "но они не связаны ими, как мы.",
                width / 2, subtitleY + 13, 0xFFD0D8E5);

        String timeStatus = snapshots.isEmpty()
                ? "СОБИРАЕМ ДАННЫЕ МИРА"
                : "ВРЕМЕННОЙ СЛОЙ " + (selectedIndex + 1) + "/" + snapshots.size()
                    + "  •  ← / → ИЛИ A / D — СМЕНА МОМЕНТА";
        context.drawCenteredTextWithShadow(textRenderer, timeStatus,
                width / 2, height - 21, 0xFF829AB5);
    }

    private static String timeLabel(List<TesseractHistory.Snapshot> snapshots, int index) {
        if (index == snapshots.size() - 1) return "NOW  //  ТЕКУЩИЙ МОМЕНТ";
        long ageSeconds = Math.max(0L,
                (snapshots.get(snapshots.size() - 1).worldTime() - snapshots.get(index).worldTime()) / 20L);
        return String.format(Locale.ROOT, "T − %02d:%02d", ageSeconds / 60L, ageSeconds % 60L);
    }

    private static int shade(int argb, int height) {
        int multiplier = Math.max(75, Math.min(190, 145 + height * 2));
        int red = (((argb >> 16) & 255) * multiplier) / 150;
        int green = (((argb >> 8) & 255) * multiplier) / 150;
        int blue = ((argb & 255) * multiplier) / 150;
        return 0xFF000000 | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void outline(DrawContext context, int x1, int y1, int x2, int y2,
                                int color, int thickness) {
        for (int i = 0; i < thickness; i++) {
            context.fill(x1 + i, y1 + i, x2 - i, y1 + i + 1, color);
            context.fill(x1 + i, y2 - i - 1, x2 - i, y2 - i, color);
            context.fill(x1 + i, y1 + i, x1 + i + 1, y2 - i, color);
            context.fill(x2 - i - 1, y1 + i, x2 - i, y2 - i, color);
        }
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1;
        int y = y1;
        while (true) {
            if (x >= 0 && y >= 0 && x < context.getScaledWindowWidth() && y < context.getScaledWindowHeight()) {
                context.fill(x, y, x + 1, y + 1, color);
            }
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_O) {
            client.setScreen(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            selectedIndex = Math.min(Math.max(0, TesseractHistory.size() - 1), selectedIndex + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) {
            if (TesseractHistory.capture(client)) {
                selectedIndex = Math.max(0, TesseractHistory.size() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_R) {
            InterstellarExperienceClient.playQuote();
            return true;
        }
        return super.keyPressed(input);
    }

    private record Portal(int centerX, int centerY, int portalWidth, int portalHeight) { }
}
