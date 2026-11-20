package dev.afanasy.interstellar.client;

import java.util.List;

/**
 * Small software 3D renderer for the cinematic Tesseract.
 * It projects sampled Minecraft blocks into a moving 3D corridor and places a
 * floating voxel astronaut in the foreground. It deliberately does not draw
 * dashboard cards or a 2D grid of time panels.
 */
public final class TesseractSceneRenderer {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;

    private static final double FOCAL = 326.0;
    private static final double NEAR = 0.28;
    private static final int MAX_WORLD_BLOCKS_PER_ECHO = 160;

    private final int[] pixels = new int[WIDTH * HEIGHT];
    private final float[] depth = new float[WIDTH * HEIGHT];
    private double time;

    public int[] render(List<TesseractHistory.Snapshot> snapshots, int selectedIndex) {
        time += 0.18;
        clearBackground();
        drawStars();
        drawGargantuaFarAway();
        drawTesseractStructure();
        drawWorldEchoes(snapshots, selectedIndex);
        drawFloatingPlayer();
        drawVignette();
        return pixels;
    }

    private void clearBackground() {
        for (int y = 0; y < HEIGHT; y++) {
            double ny = (double) y / HEIGHT;
            int r = (int) (2 + 3 * ny);
            int g = (int) (5 + 8 * ny);
            int b = (int) (13 + 17 * ny);
            for (int x = 0; x < WIDTH; x++) {
                double glow = Math.max(0, 1.0 - Math.sqrt(
                        sq((x - WIDTH * 0.5) / (WIDTH * 0.8))
                                + sq((y - HEIGHT * 0.48) / (HEIGHT * 0.9))));
                int rr = clamp(r + (int) (glow * 4));
                int gg = clamp(g + (int) (glow * 9));
                int bb = clamp(b + (int) (glow * 18));
                pixels[y * WIDTH + x] = argb(255, rr, gg, bb);
            }
        }
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);
    }

    private void drawStars() {
        for (int i = 0; i < 190; i++) {
            int x = Math.floorMod(i * 137 + 19, WIDTH);
            int baseY = Math.floorMod(i * 71 + 7, HEIGHT);
            int drift = (int) ((time * (0.25 + (i % 5) * 0.11)) % HEIGHT);
            int y = Math.floorMod(baseY + drift, HEIGHT);
            int c = (i % 7 == 0) ? 0xFFC1DDF6 : 0xFF718EAF;
            put(x, y, c);
            if (i % 29 == 0) {
                put(x + 1, y, c);
                put(x, y + 1, c);
            }
        }
    }

    /** A distant lensing ring, kept behind the spatial structure. */
    private void drawGargantuaFarAway() {
        int cx = WIDTH / 2 + (int) (Math.sin(time * 0.012) * 7);
        int cy = HEIGHT / 2 - 32;
        int rx = 39;
        int ry = 13;
        for (int y = -ry * 3; y <= ry * 3; y++) {
            for (int x = -rx * 2; x <= rx * 2; x++) {
                double outer = sq(x / (double) (rx * 2)) + sq(y / (double) (ry * 3));
                double inner = sq(x / (double) (rx * 0.72)) + sq(y / (double) (ry * 0.78));
                if (outer <= 1.0 && inner >= 0.78) {
                    double intensity = Math.max(0, 1.0 - Math.abs(inner - 1.12) * 2.2);
                    blend(cx + x, cy + y, argb((int) (75 + intensity * 95),
                            218, (int) (94 + 95 * intensity), (int) (40 + 55 * intensity)));
                }
            }
        }
        for (int y = -11; y <= 11; y++) {
            for (int x = -13; x <= 13; x++) {
                if (x * x / 169.0 + y * y / 121.0 <= 1.0) {
                    put(cx + x, cy + y, 0xFF010207);
                }
            }
        }
        // Thin, bright upper arc suggests gravitational lensing.
        for (int x = -48; x <= 48; x++) {
            double q = x / 48.0;
            int y = cy - (int) (11 * Math.sqrt(Math.max(0, 1 - q * q))) - 2;
            blend(cx + x, y, 0xA9FFE0A3);
        }
    }

    /** Receding steel-blue cubic ribs create the 4D corridor around the player. */
    private void drawTesseractStructure() {
        double travel = (time * 0.12) % 5.6;
        for (int ring = 0; ring < 7; ring++) {
            double z = 7.0 + ring * 5.6 - travel;
            if (z < 1.0) continue;
            double pulse = 0.5 + 0.5 * Math.sin(time * 0.035 + ring * 0.7);
            int steel = mix(0xFF294A68, 0xFF83B9D9, pulse * 0.22);
            double x = 6.4;
            double y = 4.2;
            double beam = 0.045 + ring * 0.002;
            box(0, y, z, x, beam, beam, steel, 0, 0, 0);
            box(0, -y, z, x, beam, beam, steel, 0, 0, 0);
            box(-x, 0, z, beam, y, beam, steel, 0, 0, 0);
            box(x, 0, z, beam, y, beam, steel, 0, 0, 0);
            box(0, 0, z, x, 0.012, 0.012, 0x553A7397, 0, 0, 0);
            box(0, 0, z, 0.012, y, 0.012, 0x553A7397, 0, 0, 0);

            // Cross braces give the repeating room a tangible 3D structure.
            if (ring % 2 == 0) {
                box(-3.2, 0, z, 0.025, y, 0.025, 0x744A7896, 0, 0, 0.22);
                box(3.2, 0, z, 0.025, y, 0.025, 0x744A7896, 0, 0, -0.22);
            }
        }
        // Long rails pass through the entire corridor, emphasizing forward flight.
        for (double x : new double[]{-6.4, 6.4}) {
            box(x, 4.2, 22, 0.035, 0.035, 17, 0x9B4A7895, 0, 0, 0);
            box(x, -4.2, 22, 0.035, 0.035, 17, 0x704A7895, 0, 0, 0);
        }
    }

    /**
     * The player's captured Minecraft terrain appears as suspended 3D fragments,
     * not flat thumbnails. The fragments are arranged around and behind the
     * floating player like repeated rooms in the film's Tesseract.
     */
    private void drawWorldEchoes(List<TesseractHistory.Snapshot> snapshots, int selectedIndex) {
        if (snapshots == null || snapshots.isEmpty()) return;

        double travel = (time * 0.12) % 5.6;
        double[][] anchors = {
                {-4.25, 2.45, 9.4}, {4.25, 2.30, 9.8},
                {-4.65, -2.35, 11.2}, {4.65, -2.40, 11.0},
                {-3.55, 0.15, 16.1}, {3.55, 0.05, 16.4},
                {0.0, 3.15, 19.5}, {0.0, -3.05, 20.0},
                {0.0, 0.0, 25.0}
        };

        for (int i = 0; i < anchors.length; i++) {
            double x = anchors[i][0];
            double y = anchors[i][1];
            double z = anchors[i][2] - travel;
            if (z < 2.4 || z > 31) continue;

            int snapshotIndex;
            if (i == 8) {
                snapshotIndex = Math.max(0, Math.min(selectedIndex, snapshots.size() - 1));
            } else {
                // Older snapshots occupy more distant windows in the corridor.
                snapshotIndex = Math.floorMod(selectedIndex - (i % Math.max(1, snapshots.size())), snapshots.size());
            }
            TesseractHistory.Snapshot snapshot = snapshots.get(snapshotIndex);
            double scale = Math.min(1.18, Math.max(0.62, z / 10.8));
            double yaw = Math.sin(time * 0.012 + i) * 0.10;
            drawEchoFrame(x, y, z, scale, i);
            drawVoxelFragment(snapshot, x, y, z + 0.12, scale, yaw, i);
        }
    }

    private void drawEchoFrame(double cx, double cy, double z, double scale, int seed) {
        double hw = 1.58 * scale;
        double hh = 1.10 * scale;
        double t = 0.045 * scale;
        int metal = (seed % 2 == 0) ? 0xA9A8C3D5 : 0x8F6D94AE;
        box(cx, cy + hh, z, hw, t, t, metal, 0, 0, 0);
        box(cx, cy - hh, z, hw, t, t, metal, 0, 0, 0);
        box(cx - hw, cy, z, t, hh, t, metal, 0, 0, 0);
        box(cx + hw, cy, z, t, hh, t, metal, 0, 0, 0);
        // A dim rear rim creates real depth instead of a UI-card border.
        double rearZ = z + 0.42 * scale;
        box(cx, cy + hh, rearZ, hw, t * 0.65, t * 0.65, 0x654E7594, 0, 0, 0);
        box(cx, cy - hh, rearZ, hw, t * 0.65, t * 0.65, 0x654E7594, 0, 0, 0);
        box(cx - hw, cy, rearZ, t * 0.65, hh, t * 0.65, 0x654E7594, 0, 0, 0);
        box(cx + hw, cy, rearZ, t * 0.65, hh, t * 0.65, 0x654E7594, 0, 0, 0);
    }

    private void drawVoxelFragment(TesseractHistory.Snapshot snapshot,
                                   double cx, double cy, double cz,
                                   double scale, double yaw, int seed) {
        int rendered = 0;
        int stepX = 3;
        int stepY = 3;
        int stepZ = 3;
        for (int gy = 0; gy < TesseractHistory.VOXEL_HEIGHT; gy += stepY) {
            for (int gz = 0; gz < TesseractHistory.MAP_SIZE; gz += stepZ) {
                for (int gx = 0; gx < TesseractHistory.MAP_SIZE; gx += stepX) {
                    int color = snapshot.voxelColorAt(gx, gy, gz);
                    if (color == 0) continue;
                    if (rendered++ >= MAX_WORLD_BLOCKS_PER_ECHO) return;

                    double bx = (gx - 8.0) * 0.145 * scale;
                    double by = (gy - 5.0) * 0.145 * scale;
                    double bz = (gz - 8.0) * 0.145 * scale;
                    double bob = Math.sin(time * 0.025 + seed * 0.7) * 0.025 * scale;
                    double halfX = 0.115 * scale;
                    double halfY = 0.115 * scale;
                    double halfZ = 0.115 * scale;
                    int lit = shade(color, 1.0 + 0.08 * Math.sin(gx + gz + seed));
                    box(cx + bx, cy + by + bob, cz + bz,
                            halfX, halfY, halfZ, lit,
                            yaw, Math.sin(time * 0.007 + seed) * 0.025, 0);
                }
            }
        }
    }

    /** The local player is represented as a 3D block astronaut floating across the lens. */
    private void drawFloatingPlayer() {
        double bob = Math.sin(time * 0.045) * 0.18;
        double drift = Math.sin(time * 0.022) * 0.34;
        double px = drift;
        double py = bob;
        double pz = 4.9;
        double roll = -0.82 + Math.sin(time * 0.017) * 0.08;
        double pitch = 0.16 + Math.sin(time * 0.02) * 0.045;
        double yaw = -0.16;

        // Soft halo behind the character.
        billboardGlow(WIDTH / 2 + (int) (drift * 34), HEIGHT / 2 - 4, 30, 0x3B73BDE0);

        // Suit torso, backpack and helmet. Parts share one slow tumbling transform.
        playerBox(px, py, pz, 0, 0.0, 0.0, 0.205, 0.315, 0.135,
                0xFF52636D, yaw, pitch, roll);
        playerBox(px, py, pz, 0, 0.02, 0.145, 0.15, 0.22, 0.055,
                0xFF80633E, yaw, pitch, roll);
        playerBox(px, py, pz, 0, 0.46, 0.0, 0.19, 0.19, 0.17,
                0xFFC69B79, yaw, pitch, roll);
        playerBox(px, py, pz, 0, 0.47, -0.17, 0.145, 0.105, 0.025,
                0xFF31556B, yaw, pitch, roll);
        playerBox(px, py, pz, 0, 0.625, 0.0, 0.19, 0.055, 0.17,
                0xFF35313A, yaw, pitch, roll);

        // Arms extended out, as if drifting weightlessly.
        double armSwing = Math.sin(time * 0.035) * 0.10;
        playerBox(px, py, pz, -0.31, 0.04, 0.0, 0.105, 0.29, 0.105,
                0xFF687C86, yaw, pitch, roll + armSwing);
        playerBox(px, py, pz, 0.31, 0.04, 0.0, 0.105, 0.29, 0.105,
                0xFF687C86, yaw, pitch, roll - armSwing);
        playerBox(px, py, pz, -0.32, -0.24, -0.005, 0.095, 0.075, 0.095,
                0xFFC69B79, yaw, pitch, roll);
        playerBox(px, py, pz, 0.32, -0.24, -0.005, 0.095, 0.075, 0.095,
                0xFFC69B79, yaw, pitch, roll);

        // Legs are slightly bent and trail behind the torso.
        playerBox(px, py, pz, -0.12, -0.48, 0.015, 0.12, 0.29, 0.12,
                0xFF354653, yaw, pitch, roll - 0.12);
        playerBox(px, py, pz, 0.12, -0.48, 0.015, 0.12, 0.29, 0.12,
                0xFF354653, yaw, pitch, roll + 0.12);
        playerBox(px, py, pz, -0.12, -0.77, -0.035, 0.125, 0.10, 0.16,
                0xFF202A34, yaw, pitch, roll);
        playerBox(px, py, pz, 0.12, -0.77, -0.035, 0.125, 0.10, 0.16,
                0xFF202A34, yaw, pitch, roll);

        // Small amber life-support lights make the silhouette readable in the dark.
        playerBox(px, py, pz, 0, -0.02, -0.152, 0.045, 0.035, 0.018,
                0xFFFFB45D, yaw, pitch, roll);
    }

    private void playerBox(double px, double py, double pz,
                           double lx, double ly, double lz,
                           double hx, double hy, double hz, int color,
                           double yaw, double pitch, double roll) {
        double scale = 1.38;
        double[] center = rotate(lx * scale, ly * scale, lz * scale, yaw, pitch, roll);
        box(px + center[0], py + center[1], pz + center[2], hx * scale, hy * scale, hz * scale,
                color, yaw, pitch, roll);
    }

    private void billboardGlow(int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                double d = Math.sqrt(x * x + y * y) / radius;
                if (d > 1) continue;
                int alpha = (int) (((color >>> 24) & 255) * sq(1 - d));
                blend(cx + x, cy + y, (color & 0x00FFFFFF) | (alpha << 24));
            }
        }
    }

    private void drawVignette() {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double dx = (x - WIDTH * 0.5) / (WIDTH * 0.72);
                double dy = (y - HEIGHT * 0.5) / (HEIGHT * 0.72);
                double d = Math.min(0.74, Math.max(0, dx * dx + dy * dy) * 0.22);
                int index = y * WIDTH + x;
                int c = pixels[index];
                int k = (int) ((1.0 - d) * 255.0);
                pixels[index] = argb(255,
                        ((c >>> 16) & 255) * k / 255,
                        ((c >>> 8) & 255) * k / 255,
                        (c & 255) * k / 255);
            }
        }
    }

    private void box(double cx, double cy, double cz,
                     double hx, double hy, double hz,
                     int color, double yaw, double pitch, double roll) {
        double[][] v = {
                {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, hy, -hz}, {-hx, hy, -hz},
                {-hx, -hy, hz}, {hx, -hy, hz}, {hx, hy, hz}, {-hx, hy, hz}
        };
        for (int i = 0; i < v.length; i++) {
            double[] r = rotate(v[i][0], v[i][1], v[i][2], yaw, pitch, roll);
            v[i][0] = r[0] + cx;
            v[i][1] = r[1] + cy;
            v[i][2] = r[2] + cz;
        }

        int[][] faces = {
                {0, 1, 2, 3}, {5, 4, 7, 6},
                {4, 0, 3, 7}, {1, 5, 6, 2},
                {3, 2, 6, 7}, {4, 5, 1, 0}
        };
        double[] light = {0.58, 0.42, 0.70, 0.78, 1.00, 0.48};
        for (int f = 0; f < faces.length; f++) {
            int[] face = faces[f];
            Projected a = project(v[face[0]][0], v[face[0]][1], v[face[0]][2]);
            Projected b = project(v[face[1]][0], v[face[1]][1], v[face[1]][2]);
            Projected c = project(v[face[2]][0], v[face[2]][1], v[face[2]][2]);
            Projected d = project(v[face[3]][0], v[face[3]][1], v[face[3]][2]);
            if (a == null || b == null || c == null || d == null) continue;
            int shaded = shade(color, light[f]);
            triangle(a, b, c, shaded);
            triangle(a, c, d, shaded);
        }
    }

    private Projected project(double x, double y, double z) {
        if (z <= NEAR) return null;
        double inv = FOCAL / z;
        return new Projected(WIDTH * 0.5 + x * inv, HEIGHT * 0.5 - y * inv, z);
    }

    private void triangle(Projected a, Projected b, Projected c, int color) {
        int minX = clamp((int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))), 0, WIDTH - 1);
        int maxX = clamp((int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))), 0, WIDTH - 1);
        int minY = clamp((int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))), 0, HEIGHT - 1);
        int maxY = clamp((int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))), 0, HEIGHT - 1);
        double area = edge(a.x, a.y, b.x, b.y, c.x, c.y);
        if (Math.abs(area) < 0.0001) return;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = edge(b.x, b.y, c.x, c.y, px, py) / area;
                double w1 = edge(c.x, c.y, a.x, a.y, px, py) / area;
                double w2 = 1.0 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = (float) (w0 * a.z + w1 * b.z + w2 * c.z);
                int index = y * WIDTH + x;
                if (z < depth[index]) {
                    depth[index] = z;
                    pixels[index] = color;
                }
            }
        }
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static double[] rotate(double x, double y, double z,
                                    double yaw, double pitch, double roll) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x1 = x * cy - z * sy;
        double z1 = x * sy + z * cy;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double y2 = y * cp - z1 * sp;
        double z2 = y * sp + z1 * cp;
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double x3 = x1 * cr - y2 * sr;
        double y3 = x1 * sr + y2 * cr;
        return new double[]{x3, y3, z2};
    }

    private void blend(int x, int y, int src) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return;
        int index = y * WIDTH + x;
        int alpha = (src >>> 24) & 255;
        int dst = pixels[index];
        int inv = 255 - alpha;
        int r = (((src >>> 16) & 255) * alpha + ((dst >>> 16) & 255) * inv) / 255;
        int g = (((src >>> 8) & 255) * alpha + ((dst >>> 8) & 255) * inv) / 255;
        int b = ((src & 255) * alpha + (dst & 255) * inv) / 255;
        pixels[index] = argb(255, r, g, b);
    }

    private void put(int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT) pixels[y * WIDTH + x] = color;
    }

    private static int shade(int argb, double light) {
        int r = clamp((int) (((argb >>> 16) & 255) * light));
        int g = clamp((int) (((argb >>> 8) & 255) * light));
        int b = clamp((int) ((argb & 255) * light));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        int r = (int) (((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = (int) (((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double sq(double value) {
        return value * value;
    }

    private record Projected(double x, double y, double z) { }
}
