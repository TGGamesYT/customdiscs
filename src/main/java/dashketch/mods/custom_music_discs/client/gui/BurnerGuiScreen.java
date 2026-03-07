package dashketch.mods.custom_music_discs.client.gui;

import dashketch.mods.custom_music_discs.network.MusicUploadPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class BurnerGuiScreen extends Screen {
    private String selectedFilePath = null;
    private Button burnButton;
    private String statusMessage = "§7No file selected";
    private boolean isUploading = false;

    public BurnerGuiScreen() {
        super(Component.literal("Disc Burner Interface"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new BurnerGuiScreen());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // --- Select File Button ---
        this.addRenderableWidget(Button.builder(Component.literal("Select Music File (MP3/WAV)"), b -> openFileChooser())
                .bounds(centerX - 100, 50, 200, 20).build());

        // --- Burn Button ---
        this.burnButton = this.addRenderableWidget(Button.builder(Component.literal("BURN TO DISC"), b -> handleBurnProcess())
                .bounds(centerX - 100, 90, 200, 20).build());

        // --- Cancel/Close ---
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(centerX - 100, this.height - 40, 200, 20).build());

        updateButtonStates();
    }

    private void openFileChooser() {
        // Run in a separate thread so the game doesn't freeze while the window is open
        Thread thread = new Thread(() -> {
            String filter = "*.mp3;*.wav";
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Audio for Disc",
                    "",
                    null,
                    "Audio Files (" + filter + ")",
                    false
            );

            if (path != null) {
                File file = new File(path);
                // Basic security: Check extension again
                if (path.toLowerCase().endsWith(".mp3") || path.toLowerCase().endsWith(".wav")) {
                    this.selectedFilePath = path;
                    this.statusMessage = "§aReady: " + file.getName();
                } else {
                    this.statusMessage = "§cInvalid file type!";
                    this.selectedFilePath = null;
                }
                updateButtonStates();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void handleBurnProcess() {
        if (selectedFilePath == null || isUploading) return;

        File file = new File(selectedFilePath);
        if (!file.exists()) return;

        isUploading = true;
        this.statusMessage = "§6Burning... Please wait.";
        updateButtonStates();

        // Start upload in a thread to keep the GUI responsive
        Thread uploadThread = new Thread(() -> {
            try {
                uploadFileInChunks(file);
                Minecraft.getInstance().execute(this::onClose);
            } catch (IOException e) {
                this.statusMessage = "§cUpload Failed!";
                this.isUploading = false;
                updateButtonStates();
            }
        });
        uploadThread.start();
    }

    @SuppressWarnings("BusyWait")
    private void uploadFileInChunks(File file) throws IOException {
        byte[] buffer = new byte[30000]; // ~30KB chunks to stay safe under packet limits
        int bytesRead;
        boolean first = true;

        try (FileInputStream fis = new FileInputStream(file)) {
            while ((bytesRead = fis.read(buffer)) != -1) {
                boolean last = (fis.available() == 0);
                byte[] chunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);

                // Send the chunk to the server
                PacketDistributor.sendToServer(new MusicUploadPayload(file.getName(), chunk, first, last));

                first = false;
                // Tiny sleep to prevent saturating the network pipe
                Thread.sleep(10);
            }
        } catch (InterruptedException ignored) {}
    }

    private void updateButtonStates() {
        if (burnButton != null) {
            burnButton.active = (selectedFilePath != null && !isUploading);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, statusMessage, centerX, 75, 0xFFFFFF);

        if (isUploading) {
            guiGraphics.drawCenteredString(this.font, "§eUploading to server...", centerX, 115, 0xFFFFFF);
        }
    }
}