package com.paulspies.reactocraft;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Radiation as a property of SPACE, one number per chunk.
 *
 * Design taken from HBM's Nuclear Tech Mod, `com.hbm.handler.radiation.ChunkRadiationHandlerSimple`.
 * That mod is 1.7.10 Forge and LGPLv3; **none of its code is used here.** Eleven years, the
 * Flattening, data components and the Forge-to-NeoForge split sit in between, so nothing would port
 * even if we wanted it to. What was worth taking is the shape of the idea, and mechanics are not
 * copyrightable.
 *
 * 🔑 WHY THIS AND NOT AN EMITTER INDEX, WHICH IS WHAT I WAS ABOUT TO BUILD.
 * A chunk is 256 blocks of ground covered by a single float. Contamination spreads by diffusing
 * into neighbouring chunks once a second, which costs one pass over a small map and gives spreading
 * and fading for free rather than me writing both.
 *
 * 🚨 THE COST COMPARISON THAT DECIDED IT:
 *     Radioactive   4,913 block reads per player per emitter, EVERY tick   ->  136 ms of a 50 ms tick
 *     this          one float per contaminated chunk, once per second      ->  nothing
 * Radioactive is retired precisely because it swept blocks. Nothing here ever reads a block.
 *
 * 🚨 UNLOADED CHUNKS ARE NOT A PROBLEM HERE, and that was Paul's specific worry. This is level
 * SavedData, not entities and not chunk attachments, so contamination exists whether or not anyone
 * is nearby. A zone does not pause because you walked away.
 */
public class ChunkRadiation extends SavedData {

    private static final String NAME = "reactocraft_radiation";
    private static final float MAX = 100_000F;
    /** Below this a chunk is treated as clean and dropped, so the map cannot grow forever. */
    private static final float FLOOR = 0.01F;

    private final Map<Long, Float> rads = new HashMap<>();

    public static ChunkRadiation get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkRadiation::new, ChunkRadiation::load), NAME);
    }

    // --- reading and writing -------------------------------------------------------------------

    public float get(ChunkPos pos) {
        return rads.getOrDefault(pos.toLong(), 0F);
    }

    public void set(ChunkPos pos, float value) {
        float clamped = Math.min(MAX, Math.max(0F, value));
        if (clamped < FLOOR) {
            if (rads.remove(pos.toLong()) != null) setDirty();
            return;
        }
        rads.put(pos.toLong(), clamped);
        setDirty();
    }

    public void add(ChunkPos pos, float amount) {
        set(pos, get(pos) + amount);
    }

    public int contaminatedChunks() {
        return rads.size();
    }

    // --- the once-a-second pass ----------------------------------------------------------------

    /**
     * Spread into the eight neighbours and lose a little to decay.
     *
     * The kernel is HBM's: most stays put, a little goes sideways, less goes diagonally. Decay is
     * ours, because HBM's diffusion only redistributes and never fades, and Kolten wants a blast
     * site to eventually clean itself up over three Minecraft days rather than being permanent.
     */
    public void diffuse(float keep, float side, float diagonal, float decay) {
        if (rads.isEmpty()) return;

        Map<Long, Float> next = new HashMap<>(rads.size() * 2);
        for (Map.Entry<Long, Float> entry : rads.entrySet()) {
            ChunkPos from = new ChunkPos(entry.getKey());
            float value = entry.getValue() * decay;
            if (value < FLOOR) continue;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int steps = Math.abs(dx) + Math.abs(dz);
                    float share = steps == 0 ? keep : steps == 1 ? side : diagonal;
                    if (share <= 0F) continue;
                    long key = new ChunkPos(from.x + dx, from.z + dz).toLong();
                    next.merge(key, value * share, Float::sum);
                }
            }
        }

        rads.clear();
        for (Map.Entry<Long, Float> entry : next.entrySet()) {
            if (entry.getValue() >= FLOOR) {
                rads.put(entry.getKey(), Math.min(MAX, entry.getValue()));
            }
        }
        setDirty();
    }

    // --- persistence ---------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Float> entry : rads.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong("p", entry.getKey());
            one.putFloat("r", entry.getValue());
            list.add(one);
        }
        tag.put("chunks", list);
        return tag;
    }

    private static ChunkRadiation load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkRadiation data = new ChunkRadiation();
        ListTag list = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            data.rads.put(one.getLong("p"), one.getFloat("r"));
        }
        return data;
    }
}
