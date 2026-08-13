package com.paulspies.reactocraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * What a reactor going up leaves behind.
 *
 * 🔑 THIS IS THE THING A DATAPACK COULD NEVER DO. On 2026-08-09 we wrote off automatic fallout as
 * impossible because no explosion event exists for a datapack. That was true of datapacks and never
 * true of mods. Kolten asked for it twice before we had the means.
 *
 * An explosion counts as nuclear if it is big enough OR if it destroyed reactor or uranium blocks,
 * so both a full meltdown and a small reactor accident contaminate, while a creeper hole does not.
 *
 * 🚨 IT DOES NOT PAINT THE GROUND HERE. All it does is drop radiation into the chunk map and set a
 * little fire. The scarring, grass to rooted dirt to mud to ash, is LandDecay's job, and it happens
 * gradually over the following Minecraft days rather than instantly. That split is what keeps a
 * blast cheap: an explosion covering 81 chunks writes 81 floats, not tens of thousands of blocks.
 */
public final class Fallout {
    private Fallout() {}

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!RadConfig.FALLOUT_ON_EXPLOSION.get()) return;
        if (!RadConfig.CHUNK_RADIATION.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (!isNuclear(level, event)) return;

        BlockPos centre = BlockPos.containing(event.getExplosion().center());
        spread(level, new ChunkPos(centre));
        ignite(level, centre);
    }

    /**
     * Big enough, or it ate something nuclear.
     *
     * The block check matters because Create Nuclear's reactor explosion is not necessarily huge,
     * and a small failure that scatters uranium should still leave the place dirty.
     */
    private static boolean isNuclear(ServerLevel level, ExplosionEvent.Detonate event) {
        if (event.getExplosion().radius() >= RadConfig.FALLOUT_MIN_RADIUS.get()) return true;

        List<? extends String> triggers = RadConfig.FALLOUT_TRIGGER_BLOCKS.get();
        if (triggers.isEmpty()) return false;

        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (triggers.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                return true;
            }
        }
        return false;
    }

    /** Rads into the chunk map, falling off with distance. Diffusion spreads it further from here. */
    private static void spread(ServerLevel level, ChunkPos centre) {
        int radius = RadConfig.FALLOUT_CHUNK_RADIUS.get();
        double peak = RadConfig.FALLOUT_RADS.get();
        ChunkRadiation rads = ChunkRadiation.get(level);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) continue;

                // Linear falloff to zero at the edge. Diffusion will soften it further within
                // seconds, so there is no point being clever about the curve here.
                double amount = peak * (1.0D - distance / (radius + 1.0D));
                if (amount <= 0.0D) continue;
                rads.add(new ChunkPos(centre.x + dx, centre.z + dz), (float) amount);
            }
        }
    }

    /**
     * The blast sets fire to whatever can burn. Kolten likes HBM's burning blast sites.
     *
     * 🔑 COPIED FROM HBM'S RULE, not invented: fire only lands on a block that is actually FLAMMABLE,
     * with air above it, at a one in five roll, inside a limited radius. Their line is
     * `if(dist < 65 && b.isFlammable(...)) if(rand.nextInt(5) == 0 && air above) setBlock(fire)`.
     *
     * ⚠️ An earlier version of this ignited ANY surface column at a flat chance, which would have set
     * fire to bare stone and to sand. Checking flammability is what makes a forest go up and a rock
     * crater stay quiet, and it is the difference between this looking deliberate and looking broken.
     *
     * Fire spreads on its own from here, which is the point and also the danger.
     */
    private static void ignite(ServerLevel level, BlockPos centre) {
        double chance = RadConfig.FALLOUT_FIRE_CHANCE.get();
        int radius = RadConfig.FALLOUT_FIRE_CHUNK_RADIUS.get();
        if (chance <= 0.0D || radius < 0) return;

        ChunkPos middle = new ChunkPos(centre);
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                if (Math.sqrt(cx * cx + cz * cz) > radius) continue;

                ChunkPos chunk = new ChunkPos(middle.x + cx, middle.z + cz);
                // Never drag a chunk into memory just to set it alight.
                if (!level.hasChunk(chunk.x, chunk.z)) continue;

                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        if (level.random.nextDouble() > chance) continue;

                        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
                        BlockPos ground = top.below();
                        if (!level.getBlockState(top).isAir()) continue;

                        BlockState fuel = level.getBlockState(ground);
                        if (!fuel.isFlammable(level, ground, Direction.UP)) continue;

                        level.setBlock(top, Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
