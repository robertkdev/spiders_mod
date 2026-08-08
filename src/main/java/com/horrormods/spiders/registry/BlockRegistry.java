package com.horrormods.spiders.registry;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.block.SingleThreadWebBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Spiders.ModID);

    public static final RegistryObject<Block> SINGLE_THREAD_WEB = BLOCKS.register("single_thread_web",
            () -> new SingleThreadWebBlock(BlockBehaviour.Properties.of(Material.WEB)
                    .strength(0.0F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .dynamicShape()));
}
