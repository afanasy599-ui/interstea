package dev.afanasy.interstellar.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side short history of the block volume around the player.
 * The recorded voxel colours are used to build the small 3D world windows in
 * the Tesseract scene. No server packets are sent and the world is not changed.
 */
public final class TesseractHistory {
    public static final int MAP_SIZE = 17;
    public static final int VOXEL_HEIGHT = 12;
    public static final int VOXEL_BELOW = 4;
    public static final int MAX_SNAPSHOTS = 9;
    private static final int RADIUS = MAP_SIZE / 2;
    private static final long AUTO_CAPTURE_INTERVAL = 200L; // 10 seconds at 20 TPS

    private static final List<Snapshot> SNAPSHOTS = new ArrayList<>();
    private static ClientWorld trackedWorld;
    private static long lastCaptureWorldTime = Long.MIN_VALUE;

    private TesseractHistory() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            trackedWorld = null;
            lastCaptureWorldTime = Long.MIN_VALUE;
            SNAPSHOTS.clear();
            return;
        }

        if (trackedWorld != client.world) {
            trackedWorld = client.world;
            SNAPSHOTS.clear();
            lastCaptureWorldTime = Long.MIN_VALUE;
            capture(client);
            return;
        }

        long worldTime = client.world.getTime();
        if (lastCaptureWorldTime == Long.MIN_VALUE || worldTime - lastCaptureWorldTime >= AUTO_CAPTURE_INTERVAL) {
            capture(client);
        }
    }

    public static boolean capture(MinecraftClient client) {
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) return false;

        int centerX = MathHelper.floor(player.getX());
        int centerY = MathHelper.floor(player.getY());
        int centerZ = MathHelper.floor(player.getZ());
        int[] colors = new int[MAP_SIZE * MAP_SIZE];
        byte[] heights = new byte[MAP_SIZE * MAP_SIZE];
        int[] voxels = new int[MAP_SIZE * MAP_SIZE * VOXEL_HEIGHT];

        int minY = centerY - VOXEL_BELOW;
        int maxY = minY + VOXEL_HEIGHT - 1;

        for (int mapZ = 0; mapZ < MAP_SIZE; mapZ++) {
            for (int mapX = 0; mapX < MAP_SIZE; mapX++) {
                int worldX = centerX + mapX - RADIUS;
                int worldZ = centerZ + mapZ - RADIUS;
                int mapIndex = mapZ * MAP_SIZE + mapX;

                // Capture a compact 17x17x12 block volume for the 3D portal.
                for (int dy = 0; dy < VOXEL_HEIGHT; dy++) {
                    int worldY = minY + dy;
                    BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    try {
                        MapColor mapColor = state.getMapColor(world, pos);
                        int rgb = mapColor.getRenderColor(MapColor.Brightness.NORMAL);
                        if (rgb != 0) {
                            voxels[(dy * MAP_SIZE + mapZ) * MAP_SIZE + mapX] = 0xFF000000 | rgb;
                        }
                    } catch (RuntimeException ignored) {
                        // A block without a map colour is left transparent in the preview.
                    }
                }

                // Also keep a compact overhead map, used by the smaller distant windows.
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    try {
                        MapColor mapColor = state.getMapColor(world, pos);
                        int rgb = mapColor.getRenderColor(MapColor.Brightness.NORMAL);
                        if (rgb != 0) {
                            colors[mapIndex] = 0xFF000000 | rgb;
                            heights[mapIndex] = (byte) MathHelper.clamp(y - centerY, -24, 18);
                        }
                    } catch (RuntimeException ignored) {
                        // Leave this cell empty if the block has no map colour.
                    }
                    break;
                }
            }
        }

        Snapshot snapshot = new Snapshot(
                centerX,
                centerY,
                centerZ,
                world.getRegistryKey().getValue().toString(),
                world.getTime(),
                world.getTimeOfDay(),
                colors,
                heights,
                voxels
        );

        if (SNAPSHOTS.size() == MAX_SNAPSHOTS) SNAPSHOTS.remove(0);
        SNAPSHOTS.add(snapshot);
        lastCaptureWorldTime = world.getTime();
        return true;
    }

    public static List<Snapshot> getSnapshots() {
        return List.copyOf(SNAPSHOTS);
    }

    public static int size() {
        return SNAPSHOTS.size();
    }

    public record Snapshot(
            int centerX,
            int centerY,
            int centerZ,
            String dimension,
            long worldTime,
            long timeOfDay,
            int[] colors,
            byte[] heights,
            int[] voxels
    ) {
        public int colorAt(int x, int z) {
            return colors[z * MAP_SIZE + x];
        }

        public int heightAt(int x, int z) {
            return heights[z * MAP_SIZE + x];
        }

        public int voxelColorAt(int x, int y, int z) {
            return voxels[(y * MAP_SIZE + z) * MAP_SIZE + x];
        }
    }
}
