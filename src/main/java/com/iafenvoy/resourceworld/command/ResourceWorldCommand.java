package com.iafenvoy.resourceworld.command;

import com.iafenvoy.resourceworld.config.ResourceWorldData;
import com.iafenvoy.resourceworld.config.WorldConfig;
import com.iafenvoy.resourceworld.config.generate.FlatGenerateOption;
import com.iafenvoy.resourceworld.config.generate.MirrorGenerateOption;
import com.iafenvoy.resourceworld.data.PositionLocator;
import com.iafenvoy.resourceworld.data.ResourceWorldHelper;
import com.iafenvoy.resourceworld.util.ObjectUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ResourceWorldCommand {
    private static final Object2LongMap<UUID> COOLDOWNS = new Object2LongLinkedOpenHashMap<>();
    private static final Object2LongMap<String> DELETE_CONFIRM = new Object2LongLinkedOpenHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, String> settings = argument("world", StringArgumentType.word()).suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD);
        SettingBuilder.appendSetting(settings, "centerX", IntegerArgumentType.integer(), IntegerArgumentType::getInteger, ResourceWorldData.Settings::getCenterX, ResourceWorldData.Settings::setCenterX);
        SettingBuilder.appendSetting(settings, "centerZ", IntegerArgumentType.integer(), IntegerArgumentType::getInteger, ResourceWorldData.Settings::getCenterZ, ResourceWorldData.Settings::setCenterZ);
        SettingBuilder.appendSetting(settings, "range", IntegerArgumentType.integer(0), IntegerArgumentType::getInteger, ResourceWorldData.Settings::getRange, ResourceWorldData.Settings::setRange);
        SettingBuilder.appendSettingOptional(settings, "spawnPoint", BlockPosArgument.blockPos(), BlockPosArgument::getBlockPos, ResourceWorldData.Settings::getSpawnPoint, ResourceWorldData.Settings::setSpawnPoint);
        SettingBuilder.appendSetting(settings, "cooldown", IntegerArgumentType.integer(0), IntegerArgumentType::getInteger, ResourceWorldData.Settings::getCooldown, ResourceWorldData.Settings::setCooldown);
        SettingBuilder.appendSetting(settings, "hideSeedHash", BoolArgumentType.bool(), BoolArgumentType::getBool, ResourceWorldData.Settings::isHideSeedHash, ResourceWorldData.Settings::setHideSeedHash);
        SettingBuilder.appendSetting(settings, "allowHomeCommand", BoolArgumentType.bool(), BoolArgumentType::getBool, ResourceWorldData.Settings::isAllowHomeCommand, ResourceWorldData.Settings::setAllowHomeCommand);

        dispatcher.register(literal("resourceworld")
                .then(literal("home")
                        .requires(CommandSourceStack::isPlayer)
                        .executes(ResourceWorldCommand::home))
                .then(literal("tp")
                        .requires(CommandSourceStack::isPlayer)
                        .then(argument("world", StringArgumentType.word())
                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD)
                                .executes(ResourceWorldCommand::teleport)
                        ))
                .then(literal("create")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(argument("world", StringArgumentType.word())
                                .then(literal("mirror")
                                        .then(argument("target", ResourceLocationArgument.id())
                                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.DIMENSIONS)
                                                .then(argument("seed", LongArgumentType.longArg()).executes(ctx -> createMirrorWorld(ctx, LongArgumentType.getLong(ctx, "seed"))))
                                                .executes(ctx -> createMirrorWorld(ctx, 0))
                                        ))
                                .then(literal("flat")
                                        .then(argument("preset", StringArgumentType.string())
                                                .then(argument("seed", LongArgumentType.longArg()).executes(ctx -> createFlatWorld(ctx, LongArgumentType.getLong(ctx, "seed"))))
                                                .executes(ctx -> createFlatWorld(ctx, 0))
                                        ))))
                .then(literal("reset")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(argument("world", StringArgumentType.word())
                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD)
                                .executes(ResourceWorldCommand::resetWorld)
                        ))
                .then(literal("delete")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(argument("world", StringArgumentType.word())
                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD)
                                .executes(ResourceWorldCommand::deleteWorld)
                        ))
                .then(literal("enable")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(argument("world", StringArgumentType.word())
                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD)
                                .executes(ctx -> setEnable(ctx, true))
                        ))
                .then(literal("disable")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(argument("world", StringArgumentType.word())
                                .suggests(com.iafenvoy.resourceworld.command.SuggestionProviders.WORLD)
                                .executes(ctx -> setEnable(ctx, false))
                        ))
                .then(literal("settings")
                        .requires(ctx -> ctx.hasPermission(2))
                        .then(settings))
        );
    }

    private static int home(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ObjectUtil.assertOrThrow(ObjectUtil.nonNullOrThrow(WorldConfig.get(source.getPlayerOrException().level().dimension()), () -> ExceptionTypes.NOT_A_RESOURCE_WORLD.create(source)).getSettings().isAllowHomeCommand(), () -> ExceptionTypes.HOME_COMMAND_BANNED.create(source));
        source.getServer().getPlayerList().respawn(source.getPlayerOrException(), true/*? >=1.21 {*/, Entity.RemovalReason.CHANGED_DIMENSION/*?}*/);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ObjectUtil.assertOrThrow(!ResourceWorldHelper.RESETTING.contains(CommandHelper.getKeyChecked(ctx)), () -> ExceptionTypes.RESETTING.create(source));
        ResourceWorldData data = CommandHelper.getDataChecked(ctx);
        ObjectUtil.assertOrThrow(data.isEnabled(), () -> ExceptionTypes.DISABLED.create(source));
        ServerLevel world = CommandHelper.getLevelChecked(ctx);
        UUID uuid = player.getUUID();
        long delta = COOLDOWNS.getOrDefault(uuid, 0) + data.getSettings().getCooldown() * 1000L - System.currentTimeMillis();
        ObjectUtil.assertOrThrow(delta <= 0, () -> ExceptionTypes.TELEPORT_COOLDOWN.create(source, String.valueOf(delta / 1000)));
        CommandHelper.sendMessage(source, "finding_position");
        BlockPos pos = ObjectUtil.nonNullOrThrow(PositionLocator.locate(world, data), () -> ExceptionTypes.CANNOT_FIND_POSITION.create(source));
        player.teleportTo(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        COOLDOWNS.put(uuid, System.currentTimeMillis());
        return 1;
    }

    private static int createMirrorWorld(CommandContext<CommandSourceStack> ctx, long seed) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (ResourceWorldHelper.createWorld(source.getServer(), CommandHelper.createKey(ctx), new MirrorGenerateOption(ResourceKey.create(Registries.LEVEL_STEM, ResourceLocationArgument.getId(ctx, "target"))), seed))
            CommandHelper.sendMessage(source, "success");
        return 1;
    }

    private static int createFlatWorld(CommandContext<CommandSourceStack> ctx, long seed) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (ResourceWorldHelper.createWorld(source.getServer(), CommandHelper.createKey(ctx), new FlatGenerateOption(StringArgumentType.getString(ctx, "preset")), seed))
            CommandHelper.sendMessage(source, "success");
        return 1;
    }

    private static int resetWorld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceWorldHelper.reset(CommandHelper.getLevelChecked(ctx));
        return 1;
    }

    private static int deleteWorld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = CommandHelper.getLevelChecked(ctx);
        String id = ResourceWorldHelper.resolveId(world.dimension());
        if (DELETE_CONFIRM.containsKey(id) && DELETE_CONFIRM.getLong(id) + 60 * 1000 >= System.currentTimeMillis()) {
            ResourceWorldHelper.deleteWorld(source.getServer(), world);
            CommandHelper.sendMessage(source, "success");
            DELETE_CONFIRM.removeLong(id);
        } else {
            DELETE_CONFIRM.put(id, System.currentTimeMillis());
            CommandHelper.sendMessage(source, "confirm_in_60s");
        }
        return 1;
    }

    private static int setEnable(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CommandHelper.getDataChecked(ctx).setEnabled(enable);
        WorldConfig.saveConfig();
        CommandHelper.sendMessage(source, "success");
        return 1;
    }
}
