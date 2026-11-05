package com.hardest.client.sound;

import com.hardest.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

/**
 * Звуки «в голове» игрока (реклама, интерфейс) и музыка битвы с драконом.
 */
public final class ClientSounds {
    private static UiSound music;

    public static class UiSound extends AbstractSoundInstance {
        public UiSound(SoundEvent ev, SoundCategory cat, float volume, boolean loop) {
            super(ev, cat, SoundInstance.createRandom());
            this.volume = volume;
            this.pitch = 1f;
            this.repeat = loop;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }
    }

    public static void ui(String sfx, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(new UiSound(ModSounds.sfx(sfx), SoundCategory.MASTER, volume, false));
    }

    public static void ui(String sfx, float volume, float pitch) {
        UiSound s = new UiSound(ModSounds.sfx(sfx), SoundCategory.MASTER, volume, false);
        s.setPitch(pitch);
        MinecraftClient.getInstance().getSoundManager().play(s);
    }

    public static void voiceLine(String line, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(new UiSound(ModSounds.voice(line), SoundCategory.VOICE, volume, false));
    }

    /** Музыка играет, пока в Крае жив дракон. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean want = false;
        if (mc.world != null && mc.player != null && mc.world.getRegistryKey() == World.END) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof EnderDragonEntity d && d.isAlive() && d.ticksSinceDeath == 0 && d.squaredDistanceTo(mc.player) < 250 * 250) {
                    want = true;
                    break;
                }
            }
        }
        if (want && music == null) {
            mc.getMusicTracker().stop();
            music = new UiSound(ModSounds.get("music.dragon"), SoundCategory.MUSIC, 0.85f, true);
            mc.getSoundManager().play(music);
        } else if (!want && music != null) {
            mc.getSoundManager().stop(music);
            music = null;
        }
        if (music != null && mc.world != null && mc.world.getTime() % 40 == 0) {
            mc.getMusicTracker().stop();
            if (!mc.getSoundManager().isPlaying(music)) mc.getSoundManager().play(music);
        }
    }

    public static void stopAll() {
        if (music != null) MinecraftClient.getInstance().getSoundManager().stop(music);
        music = null;
    }

    private ClientSounds() {
    }
}
