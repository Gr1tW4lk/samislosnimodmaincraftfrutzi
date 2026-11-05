package com.hardest.events;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Что мир помнит про каждого игрока: когда следующая налоговая, курьер, реклама; был ли он в Незере/Крае.
 */
public class EventState extends PersistentState {
    public static final class PlayerData {
        public long nextTax, nextCourier, nextAd;
        public boolean visitedNether, visitedEnd;
        public int bloodMoons;
    }

    public final Map<UUID, PlayerData> players = new HashMap<>();

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, u -> new PlayerData());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        players.forEach((id, d) -> {
            NbtCompound c = new NbtCompound();
            c.putLong("tax", d.nextTax);
            c.putLong("courier", d.nextCourier);
            c.putLong("ad", d.nextAd);
            c.putBoolean("nether", d.visitedNether);
            c.putBoolean("end", d.visitedEnd);
            c.putInt("bloodMoons", d.bloodMoons);
            all.put(id.toString(), c);
        });
        nbt.put("players", all);
        return nbt;
    }

    public static EventState fromNbt(NbtCompound nbt) {
        EventState s = new EventState();
        NbtCompound all = nbt.getCompound("players");
        for (String k : all.getKeys()) {
            try {
                NbtCompound c = all.getCompound(k);
                PlayerData d = new PlayerData();
                d.nextTax = c.getLong("tax");
                d.nextCourier = c.getLong("courier");
                d.nextAd = c.getLong("ad");
                d.visitedNether = c.getBoolean("nether");
                d.visitedEnd = c.getBoolean("end");
                d.bloodMoons = c.getInt("bloodMoons");
                s.players.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    public static EventState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(EventState::fromNbt, EventState::new, "hardest_events");
    }
}
