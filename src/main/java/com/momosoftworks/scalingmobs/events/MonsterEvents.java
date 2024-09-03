package com.momosoftworks.scalingmobs.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.momosoftworks.scalingmobs.config.ScalingMobsConfig;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;

import java.lang.reflect.Method;



@Mod.EventBusSubscriber
public class MonsterEvents
{
    @SubscribeEvent
    public static void onMobSpawn(EntityJoinLevelEvent event)
    {
        double allPlayTime = 0;
        int amountPlayer = 0;
        double playerPlayTime;
        double maxPlayTimeRaw = 0; // 用于记录在线时间最长的玩家的在线时间
        // 确保事件发生在服务端世界
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 遍历所有在线玩家
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                playerPlayTime= serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
                // 获取玩家的游戏时间，并累加到 allPlayTime 中
                allPlayTime +=playerPlayTime;
                amountPlayer +=1;
                // 如果当前玩家的在线时间比 maxPlayTimeRaw 大，则更新 maxPlayTimeRaw
                if (playerPlayTime > maxPlayTimeRaw) {
                    maxPlayTimeRaw = playerPlayTime;
                }
            }
        }

        // 根据 allPlayTime 进行后续处理
        if (event.getEntity() instanceof LivingEntity living && isScalingMob(living))
        {
            if (amountPlayer==0) {amountPlayer=1;}
            double avgPlayTime =  (allPlayTime / 24000)/amountPlayer;
            double maxPlayTime =  (maxPlayTimeRaw / 24000);
            int currentDay =(int) (avgPlayTime+ScalingMobsConfig.getInstance().getRinkoRatio()*(maxPlayTime-avgPlayTime));
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance damage = living.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);

            float currentHealthPercent = living.getHealth() / living.getMaxHealth();
            boolean exponential = ScalingMobsConfig.getInstance().areStatsExponential();

            if (damage != null)
            {
                double damageRate = ScalingMobsConfig.getInstance().getMobDamageRate();
                double damageMax = ScalingMobsConfig.getInstance().getMobDamageMax();
                double baseDamage = ScalingMobsConfig.getInstance().getMobDamageBase();

                damage.addTransientModifier(new AttributeModifier("ScalingMobs:DamageBase",
                                                                  baseDamage - 1,
                                                                  AttributeModifier.Operation.MULTIPLY_BASE));
                damage.addTransientModifier(new AttributeModifier("ScalingMobs:Damage",
                                                                  Math.min(damageMax - 1, getStatIncrease(damageRate, currentDay, exponential)),
                                                                  AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            if (maxHealth != null)
            {
                double baseHealth = ScalingMobsConfig.getInstance().getMobHealthBase();
                double healthRate = ScalingMobsConfig.getInstance().getMobHealthRate();
                double healthMax = ScalingMobsConfig.getInstance().getMobHealthMax();

                maxHealth.addTransientModifier(new AttributeModifier("ScalingMobs:HealthBase",
                                                                     baseHealth - 1,
                                                                     AttributeModifier.Operation.MULTIPLY_BASE));
                maxHealth.addTransientModifier(new AttributeModifier("ScalingMobs:Health",
                                                                     Math.min(healthMax - 1, getStatIncrease(healthRate, currentDay, exponential)),
                                                                     AttributeModifier.Operation.MULTIPLY_TOTAL));

                living.setHealth(living.getMaxHealth() * currentHealthPercent);
            }

            if (speed != null)
            {
                double speedRate = ScalingMobsConfig.getInstance().getMobSpeedRate();
                double speedMax = ScalingMobsConfig.getInstance().getMobSpeedMax();
                double baseSpeed = ScalingMobsConfig.getInstance().getMobSpeedBase();

                speed.addTransientModifier(new AttributeModifier("ScalingMobs:SpeedBase", baseSpeed - 1, AttributeModifier.Operation.MULTIPLY_BASE));
                speed.addTransientModifier(new AttributeModifier("ScalingMobs:Speed",
                                                                 Math.min(speedMax - 1, getStatIncrease(speedRate, currentDay, exponential)),
                                                                 AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    public static double getMultipliedStat(double stat, double base, double rate, double max, int day, boolean exponential)
    {
        if (exponential)
        {
            return Math.min(max, (stat * base) * Math.pow(1 + rate, day));
        }
        else
        {
            return Math.min(max, (stat * base) * (1 + (day * rate)));
        }
    }

    // This method returns the increase for a stat, meant for attribute modifiers
    // i.e. if the method returns 0.5, that's a 50% increase
    public static double getStatIncrease(double rate, int day, boolean exponential)
    {
        if (exponential)
        {   return Math.pow(1 + rate, day) - 1;
        }
        else
        {   return (day * rate);
        }
    }

    // Piercing Damage
    @SubscribeEvent
    public static void onMobDamage(LivingDamageEvent event)
    {
        if (event.getEntity() instanceof Player
        && event.getSource().getEntity() instanceof LivingEntity living && isScalingMob(living))
        {
            int currentDay = (int) (event.getEntity().level().getDayTime() / 24000L);
            double scaleRate = ScalingMobsConfig.getInstance().getPiercingRate();
            double maxPiercing = ScalingMobsConfig.getInstance().getMaxPiercing();
            float damage = event.getAmount();

            float normalDamage = (float) (damage * Math.max(0, 1 - currentDay * Math.min(scaleRate, maxPiercing)));
            float armorPierceDamage = (float) Math.min(damage, damage * (currentDay * Math.min(scaleRate, maxPiercing)));

            event.setAmount(normalDamage);
            event.getEntity().setHealth(event.getEntity().getHealth() - armorPierceDamage);
        }
    }

    // Multiply mob drops
    @SubscribeEvent
    public static void onMobDrop(LivingDropsEvent event)
    {
        if (isScalingMob(event.getEntity()))
        {
            double dropRate = ScalingMobsConfig.getInstance().getMobDropsRate();
            double dropBase = ScalingMobsConfig.getInstance().getMobDropsBase();
            double maxDrops = ScalingMobsConfig.getInstance().getMobDropsMax();
            int currentDay = (int) (event.getEntity().level().getDayTime() / 24000L);

            double multiplier = getMultipliedStat(1 + event.getLootingLevel(), dropBase, dropRate, maxDrops, currentDay, false);
            int repetitions = (int) Math.floor(multiplier);
            double remainder = multiplier - repetitions;

            if (Math.random() < remainder) repetitions++;

            try
            {
                Method dropLoot = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "func_213354_a", DamageSource.class, boolean.class);
                Method dropSpecialItems = ObfuscationReflectionHelper.findMethod(LivingEntity.class, "func_213333_a", DamageSource.class, int.class, boolean.class);

                for (int i = 0; i < repetitions; i++)
                {
                    dropLoot.invoke(event.getEntity(), event.getSource(), event.isRecentlyHit());
                    dropSpecialItems.invoke(event.getEntity(), event.getSource(), event.getLootingLevel(), event.isRecentlyHit());
                }
            }
            catch (Exception e) {}
        }
    }

    public static boolean isScalingMob(LivingEntity entity)
    {
        return entity instanceof Monster
            || ScalingMobsConfig.getInstance().getMobWhitelist().contains(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString());
    }
}
