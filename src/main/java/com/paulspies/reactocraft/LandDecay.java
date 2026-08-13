package com.paulspies.reactocraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The land rots where the radiation is.
 *
 * Design taken from HBM's `FalloutConfigJSON`, which drives its blast scarring off a data table of
 * "this block becomes that block" rather than hardcoding it. No HBM code is used; it is 1.7.10 and
 * LGPLv3, and mechanics are not copyrightable.
 *
 * 🔑 THE TIER IS THE CHUNK'S RADIATION, NOT THE DISTANCE FROM A BLAST. That unifies everything: a
 * thrown Radiation potion only ever reaches LIGHT, so it kills grass and leaves shrubs, while a
 * reactor going up reaches HEAVY and produces ash, mud and burnt trunks. One rule, and the severity
 * falls out of the number we already track.
 *
 * 🚨 IT IS GRADUAL, ON PURPOSE. Paul and Kolten, 2026-08-13: the land should decay over one to three
 * Minecraft days, not flip in a single frame. Each pass converts a few blocks per chunk, so you
 * watch a place die.
 *
 * ⚠️ THE ONE HONEST LIMIT: block edits need a LOADED chunk. A chunk nobody has visited cannot be
 * rewritten, so the visuals only advance where somebody is. The radiation itself never had this
 * problem, because that is level data. This means a distant blast site looks untouched until you
 * walk out to it, and then it rots while you watch.
 */
public final class LandDecay {
    private LandDecay() {}

    private static Map<Block, Block> light;
    private static Map<Block, Block> mid;
    private static Map<Block, Block> heavy;

    /** Rebuilt whenever the config is reloaded, so edits take effect on restart without a rebuild. */
    private static Map<Block, Block> parse(List<? extends String> lines) {
        Map<Block, Block> map = new HashMap<>();
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            Block from = block(line.substring(0, eq).trim());
            Block to = block(line.substring(eq + 1).trim());
            // A missing block means the mod that owned it is not installed. Skip quietly rather
            // than mapping something to air by accident.
            if (from == null || to == null) continue;
            map.put(from, to);
        }
        return map;
    }

    private static Block block(String id) {
        if (id.equals("minecraft:air")) return Blocks.AIR;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) return null;
        return BuiltInRegistries.BLOCK.get(key);
    }

    private static void ensureLoaded() {
        if (light != null) return;
        light = parse(RadConfig.DECAY_LIGHT.get());
        mid = parse(RadConfig.DECAY_MID.get());
        heavy = parse(RadConfig.DECAY_HEAVY.get());
    }

    /** Called once per radiation pass, after diffusion. */
    public static void tick(ServerLevel level, ChunkRadiation rads) {
        if (!RadConfig.LAND_DECAY.get()) return;
        ensureLoaded();

        int attempts = RadConfig.DECAY_BLOCKS_PER_PASS.get();
        double chance = RadConfig.DECAY_CHANCE.get();
        float lightAt = (float) (double) RadConfig.MIN_DOSE_RADS.get();
        float midAt = (float) (double) RadConfig.DECAY_MID_RADS.get();
        float heavyAt = (float) (double) RadConfig.DECAY_HEAVY_RADS.get();

        for (ChunkPos pos : rads.contaminated()) {
            float value = rads.get(pos);
            if (value < lightAt) continue;

            // 🚨 Never force-load. Rewriting a chunk nobody is near would drag it into memory and
            // is exactly the kind of cost this whole design exists to avoid.
            if (!level.hasChunk(pos.x, pos.z)) continue;

            Map<Block, Block> table = value >= heavyAt ? heavy : value >= midAt ? mid : light;
            if (table.isEmpty()) continue;

            for (int i = 0; i < attempts; i++) {
                if (level.random.nextDouble() > chance) continue;
                rot(level, pos, table);
            }
        }
    }

    /** One random column in the chunk: find the surface, and convert it or the plant standing on it. */
    private static void rot(ServerLevel level, ChunkPos chunk, Map<Block, Block> table) {
        int x = chunk.getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getMinBlockZ() + level.random.nextInt(16);

        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

        // Try the surface block, then the one under it. The top is often a plant, and once the plant
        // is gone the ground beneath it should start going too.
        for (BlockPos pos : new BlockPos[]{top, top.below()}) {
            BlockState state = level.getBlockState(pos);
            Block replacement = table.get(state.getBlock());
            if (replacement == null) continue;
            level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    /** Called when the config reloads so a hand edit on the box actually takes effect. */
    public static void invalidate() {
        light = null;
        mid = null;
        heavy = null;
    }
}
