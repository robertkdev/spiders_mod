package com.horrormods.spiders.entity;

import com.horrormods.spiders.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WebShotEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME_TICKS = 80;
    private static final int SLOW_TICKS = 140;
    private static final int BLINDNESS_TICKS = 100;
    private static final int SLOW_AMPLIFIER = 4;
    private static final double IMPACT_CONTROL_RADIUS = 1.75D;

    public WebShotEntity(EntityType<? extends WebShotEntity> type, Level level) {
        super(type, level);
    }

    public WebShotEntity(Level level, LivingEntity owner) {
        super(EntityRegistry.WEB_SHOT.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Blocks.COBWEB.asItem();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide && this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity target) || entity == this.getOwner()) {
            return;
        }

        applyControlEffects(target);
        tryPlaceWeb(target.blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        BlockPos webPos = hit.getBlockPos().relative(hit.getDirection());
        tryPlaceWeb(webPos);
        applyControlEffectsNear(hit.getLocation());
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!this.level.isClientSide) {
            this.discard();
        }
    }

    @Override
    protected float getGravity() {
        return 0.025F;
    }

    private boolean tryPlaceWeb(BlockPos preferredPos) {
        if (this.level.isClientSide) {
            return false;
        }

        BlockPos[] candidates = {
                preferredPos,
                preferredPos.above(),
                preferredPos.below()
        };
        for (BlockPos candidate : candidates) {
            if (this.level.isEmptyBlock(candidate)) {
                this.level.setBlockAndUpdate(candidate, Blocks.COBWEB.defaultBlockState());
                return true;
            }
        }
        return false;
    }

    private void applyControlEffects(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0));
    }

    private void applyControlEffectsNear(Vec3 impact) {
        if (this.level.isClientSide) {
            return;
        }

        Entity owner = this.getOwner();
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        AABB area = new AABB(impact, impact).inflate(IMPACT_CONTROL_RADIUS);
        for (LivingEntity candidate : this.level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != owner)) {
            double distance = candidate.distanceToSqr(impact);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            applyControlEffects(nearest);
        }
    }
}
