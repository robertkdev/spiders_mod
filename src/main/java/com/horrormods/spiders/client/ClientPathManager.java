package com.horrormods.spiders.client;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class ClientPathManager {
    private static List<BlockPos> currentPath = new ArrayList<>();

    public static void setPath(List<BlockPos> path) {
        currentPath = path;
    }

    public static List<BlockPos> getPath() {
        return currentPath;
    }
}