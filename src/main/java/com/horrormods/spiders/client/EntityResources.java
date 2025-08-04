package com.horrormods.spiders.client;

import com.horrormods.spiders.Spiders;
import net.minecraft.resources.ResourceLocation;

public class EntityResources {
    // You can keep the bat or remove it
    public static final ResourceLocation BAT_MODEL = new ResourceLocation(Spiders.ModID, "geo/bat.geo.json");
    public static final ResourceLocation BAT_TEXTURE = new ResourceLocation(Spiders.ModID, "textures/model/entity/bat.png");
    public static final ResourceLocation BAT_ANIMATIONS = new ResourceLocation(Spiders.ModID, "animations/bat.animation.json");

    // Add these for your spider
    public static final ResourceLocation SPIDER_MODEL = new ResourceLocation(Spiders.ModID, "geo/ground_spider_model.geo.json");
    public static final ResourceLocation SPIDER_TEXTURE = new ResourceLocation(Spiders.ModID, "textures/entity/ground_spider_texture.png");
    public static final ResourceLocation SPIDER_ANIMATIONS = new ResourceLocation(Spiders.ModID, "animations/ground_spider.animation.json");
}