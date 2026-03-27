package com.iafenvoy.resourceworld.mixin;

import com.iafenvoy.resourceworld.config.ResourceWorldData;
import com.iafenvoy.resourceworld.config.WorldConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? neoforge {
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @ModifyExpressionValue(method = "sendLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getWorldBorder()Lnet/minecraft/world/level/border/WorldBorder;"))
    private WorldBorder handleBorderPacketTarget(WorldBorder original, @Local(argsOnly = true) ServerLevel world) {
        //I can force redirect all, but I modify resource worlds only for better capability.
        ResourceWorldData config = WorldConfig.get(world.dimension());
        return config == null ? original : world.getWorldBorder();
    }

    //? neoforge {
    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;firePlayerLoggedIn(Lnet/minecraft/server/level/ServerPlayer;)V", remap = false),
            remap = false,
            require = 0
    )
    private void resourceWorld$beforePlayerLoggedInEvent(Connection connection, ServerPlayer player, @Coerce Object cookie, CallbackInfo ci) {
        this.resourceWorld$forceOverworldLoginIfNeeded(player);
    }

    @Inject(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;firePlayerLoggedIn(Lnet/minecraft/server/level/ServerPlayer;)V", remap = false),
            remap = false,
            require = 0
    )
    private void resourceWorld$beforePlayerLoggedInEvent(Connection connection, ServerPlayer player, CallbackInfo ci) {
        this.resourceWorld$forceOverworldLoginIfNeeded(player);
    }

    private void resourceWorld$forceOverworldLoginIfNeeded(ServerPlayer player) {
        if (WorldConfig.get(player.level().dimension()) == null) return;
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        player.teleportTo(overworld, overworld.getSharedSpawnPos().getX() + 0.5, overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ() + 0.5, player.getYRot(), player.getXRot());
    }
    //?}
}
