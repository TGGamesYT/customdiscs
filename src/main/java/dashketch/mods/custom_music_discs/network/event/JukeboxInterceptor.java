package dashketch.mods.custom_music_discs.network.event;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.essentials.custom_background_music.AudioManager;
import org.essentials.custom_background_music.KeyBindings;

import java.io.File;

@EventBusSubscriber(modid = "custom_music_discs", bus = EventBusSubscriber.Bus.GAME)
public class JukeboxInterceptor {

    // We use this to track if the current sound is "ours"
    private static boolean isPlayingJukebox = false;

    @SubscribeEvent
    public static void onJukeboxRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();

        if (level.getBlockState(pos).is(Blocks.JUKEBOX)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

            if (customData != null && customData.copyTag().contains("SelectedSong")) {
                String songName = customData.copyTag().getString("SelectedSong");

                if (level.isClientSide) {
                    AudioManager am = AudioManager.getInstance();
                    File musicFile = resolveMusicFile(songName);

                    am.stop();
                    if (am.loadMusicFile(musicFile)) {
                        am.play();
                        isPlayingJukebox = true; // Mark that we are in Jukebox mode
                    }
                } else {
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                        jukebox.setTheItem(stack.copy());
                        if (!player.isCreative()) stack.shrink(1);
                    }
                }
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Only run this logic if our Jukebox flag is active and music is playing
        if (isPlayingJukebox && AudioManager.getInstance().isPlaying()) {

            // This force-clears the 'click' counter and 'pressed' state
            // for the other mod's keybindings every single tick.
            while (KeyBindings.STOP_MUSIC.consumeClick()) {
            }
            while (KeyBindings.PAUSE_PLAY_MUSIC.consumeClick()) {
            }
            while (KeyBindings.OPEN_MUSIC_GUI.consumeClick()) {
            }

            // Additionally, force the 'isDown' state to false
            // (This prevents 'held' key logic)
            KeyBindings.STOP_MUSIC.setDown(false);
            KeyBindings.PAUSE_PLAY_MUSIC.setDown(false);
            KeyBindings.OPEN_MUSIC_GUI.setDown(false);
        } else {
            // If the music naturally ends, reset flag
            if (isPlayingJukebox && !AudioManager.getInstance().isPlaying()) {
                isPlayingJukebox = false;
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(Blocks.JUKEBOX)) {
            AudioManager am = AudioManager.getInstance();

            am.stop();
        }
    }

    // Add this to handle stopping when the player punches the jukebox (ejects disc)
    @SubscribeEvent
    public static void onJukeboxPunch(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).is(Blocks.JUKEBOX) && event.getLevel().isClientSide) {
            AudioManager.getInstance().stop();
            isPlayingJukebox = false;
        }
    }

    @SubscribeEvent
    public static void onGuiOpen(ScreenEvent.Opening event) {
        if (isPlayingJukebox && event.getNewScreen() instanceof org.essentials.custom_background_music.MusicGuiScreen) {
            event.setCanceled(true);
            assert Minecraft.getInstance().player != null;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§cManual controls disabled for Jukebox discs!"), true);
        }
    }

    private static File resolveMusicFile(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        File mcDir = mc.gameDirectory;
        if (mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            return new File(mcDir, "saves/" + worldName + "/config/uploaded_music/" + fileName);
        }
        return new File(mcDir, "config/uploaded_music/" + fileName);
    }
}