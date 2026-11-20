package dev.afanasy.interstellar.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Full-screen cinematic sequence: a floating player inside a 3D Minecraft Tesseract. */
public final class TesseractScreen extends Screen {
    private final TesseractSceneRenderer scene = new TesseractSceneRenderer();
    private int selectedIndex;
    private int framesUntilUpload;
    private int sceneTicks;
    private NativeImageBackedTexture sceneTexture;
    private Identifier sceneTextureId;

    public TesseractScreen() {
        super(Text.literal("Interstellar Experience — Tesseract"));
        this.selectedIndex = Math.max(0, TesseractHistory.size() - 1);
    }

    @Override
    protected void init() {
        super.init();
        if (sceneTexture != null && sceneTextureId != null) {
            client.getTextureManager().destroyTexture(sceneTextureId);
        }
        sceneTexture = new NativeImageBackedTexture(
                "interstellar-tesseract-scene",
                TesseractSceneRenderer.WIDTH,
                TesseractSceneRenderer.HEIGHT,
                false
        );
        sceneTextureId = Identifier.of("interstellar_experience", "dynamic/tesseract_scene");
        client.getTextureManager().registerTexture(sceneTextureId, sceneTexture);
        framesUntilUpload = 0;
    }

    @Override
    public boolean shouldPause() {
        // Keep the client world ticking so new temporal echoes can be captured in the background.
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        sceneTicks++;
        List<TesseractHistory.Snapshot> snapshots = TesseractHistory.getSnapshots();
        if (!snapshots.isEmpty()) {
            selectedIndex = Math.max(0, Math.min(selectedIndex, snapshots.size() - 1));
        } else {
            selectedIndex = 0;
        }

        // The software 3D scene is rendered at 480x270 and scaled to the full game window.
        // Upload every second frame to keep CPU and GPU transfer costs reasonable.
        if (framesUntilUpload-- <= 0) {
            framesUntilUpload = 1;
            uploadScene(scene.render(snapshots, selectedIndex));
        }

        if (sceneTextureId != null) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    sceneTextureId,
                    0, 0,
                    0.0f, 0.0f,
                    width, height,
                    TesseractSceneRenderer.WIDTH,
                    TesseractSceneRenderer.HEIGHT
            );
        } else {
            context.fill(0, 0, width, height, 0xFF020711);
        }

        drawFilmSubtitles(context, snapshots);
    }

    private void uploadScene(int[] frame) {
        if (sceneTexture == null) return;
        NativeImage image = sceneTexture.getImage();
        if (image == null) return;
        int index = 0;
        for (int y = 0; y < TesseractSceneRenderer.HEIGHT; y++) {
            for (int x = 0; x < TesseractSceneRenderer.WIDTH; x++) {
                image.setColorArgb(x, y, frame[index++]);
            }
        }
        sceneTexture.upload();
    }

    private void drawFilmSubtitles(DrawContext context, List<TesseractHistory.Snapshot> snapshots) {
        // Cinematic captions only: no dashboard, timeline cards, or panel grid.
        context.drawTextWithShadow(textRenderer, "INTERSTELLAR", 18, 15, 0xDCEAF4FF);
        context.drawTextWithShadow(textRenderer, "T H E   T E S S E R A C T", 18, 29, 0xB5AFC5D8);

        if (sceneTicks < 420) {
            int alpha = Math.max(90, 235 - Math.max(0, sceneTicks - 300));
            int color = (alpha << 24) | 0x00F2EBDD;
            context.drawCenteredTextWithShadow(textRenderer,
                    "У них есть доступ к бесконечности времени и пространству,",
                    width / 2, height - 52, color);
            context.drawCenteredTextWithShadow(textRenderer,
                    "но они не связаны ими, как мы.",
                    width / 2, height - 38, color);
        }

        String moment = snapshots.isEmpty()
                ? "МИР ЗАПИСЫВАЕТСЯ — НАЖМИ P, ЧТОБЫ СОХРАНИТЬ МОМЕНТ"
                : "ЭХО " + (selectedIndex + 1) + "/" + snapshots.size()
                    + "   ← / → ИЛИ A / D — ДРУГОЙ МОМЕНТ   P — ЗАПИСАТЬ";
        context.drawCenteredTextWithShadow(textRenderer, moment,
                width / 2, height - 17, 0xAFC5D8E8);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_O) {
            close();
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

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }

    @Override
    public void removed() {
        if (sceneTextureId != null && client != null) {
            client.getTextureManager().destroyTexture(sceneTextureId);
            sceneTextureId = null;
            sceneTexture = null;
        }
        super.removed();
    }
}
