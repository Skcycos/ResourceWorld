package com.iafenvoy.resourceworld.mixin;

import com.google.common.collect.ImmutableList;
import com.iafenvoy.resourceworld.MixinCache;
import com.iafenvoy.resourceworld.ResourceWorld;
import com.iafenvoy.resourceworld.accessor.MinecraftServerAccessor;
import com.iafenvoy.resourceworld.config.WorldConfig;
import com.iafenvoy.resourceworld.config.generate.GenerateOption;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends BlockableEventLoop<TickTask> implements MinecraftServerAccessor {
    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow
    @Final
    private Executor executor;

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Shadow
    @Final
    protected WorldData worldData;

    @Shadow
    @Final
    private LayeredRegistryAccess<RegistryLayer> registries;

    //? !fabric {
    @Shadow(remap = false)
    public abstract void markWorldsDirty();
    //?}

    protected MinecraftServerMixin(String name) {
        super(name);
    }

    @Unique
    private MinecraftServer resourceWorld$self() {
        return (MinecraftServer) (Object) this;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onServerStart(CallbackInfo ci) {
        WorldConfig.bootstrap(MixinCache.SERVER = this.resourceWorld$self());
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void createResourceWorlds(CallbackInfo ci) {
        WorldConfig.initResourceWorld(this::resourceWorld$createLevel);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onServerStop(CallbackInfo ci) {
        WorldConfig.stop();
        MixinCache.SERVER = null;
    }

    @Override
    public boolean resourceWorld$createLevel(ResourceKey<Level> key, GenerateOption option) {
        try {
            ServerLevelData data = this.worldData.overworldData();
            boolean bl = this.worldData.isDebugWorld();
            WorldOptions generatorOptions = this.worldData.worldGenOptions();
            long l = generatorOptions.seed();
            long m = BiomeManager.obfuscateSeed(l);
            DerivedLevelData unmodifiableLevelProperties = new DerivedLevelData(this.worldData, data);
            LevelStem stem = option.createStem(this.resourceWorld$self().registryAccess());
            ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, key.location());
            Registry<LevelStem> stemRegistry = this.resourceWorld$self().registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            if (stemRegistry.get(stemKey.location()) == null) {
                try {
                    Registry.register(stemRegistry, stemKey.location(), stem);
                } catch (Exception e) {
                    ResourceWorld.LOGGER.warn("Failed to register LevelStem for {}", stemKey.location(), e);
                }
            }
            this.levels.put(key, new ServerLevel(this.resourceWorld$self(), this.executor, this.storageSource, unmodifiableLevelProperties, key, stem, /*? >=1.20.5 {*/StoringChunkProgressListener.createFromGameruleRadius/*?} else {*//*new StoringChunkProgressListener*//*?}*/(16), bl, m, ImmutableList.of(), false, null));
            //? !fabric {
            this.markWorldsDirty();
            //?}
            return true;
        } catch (Exception e) {
            ResourceWorld.LOGGER.error("Failed to create world", e);
            return false;
        }
    }

    @Override
    public void resourceWorld$removeWorld(ResourceKey<Level> key) {
        this.levels.remove(key);
        //? !fabric {
        this.markWorldsDirty();
        //?}
    }
}
