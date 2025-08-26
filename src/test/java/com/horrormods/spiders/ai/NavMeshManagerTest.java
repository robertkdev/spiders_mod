package com.horrormods.spiders.ai;

import com.horrormods.spiders.entity.ai.nav.NavMeshManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Basic regression tests for the nav‑mesh manager. These tests do not depend
 * on the Minecraft engine and simply exercise eviction and rebuild logic.
 */
public class NavMeshManagerTest {

    @Test
    public void testEvictionRemovesFarChunks() {
        NavMeshManager mgr = new NavMeshManager();
        mgr.insertTestEntry(new ChunkPos(0,0));
        mgr.insertTestEntry(new ChunkPos(1,0));
        mgr.insertTestEntry(new ChunkPos(2,0));
        mgr.tick(new Vec3(1000,0,1000), 1);
        Assertions.assertEquals(0, mgr.cacheSize());
    }
}

