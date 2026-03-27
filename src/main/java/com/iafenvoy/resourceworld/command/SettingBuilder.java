package com.iafenvoy.resourceworld.command;

import com.iafenvoy.resourceworld.config.ResourceWorldData;
import com.iafenvoy.resourceworld.config.WorldConfig;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SettingBuilder {
    public static <T> void appendSetting(ArgumentBuilder<CommandSourceStack, ?> builder, String name, ArgumentType<?> type, BiFunction<CommandContext<CommandSourceStack>, String, T> parser, Function<ResourceWorldData.Settings, T> getter, BiConsumer<ResourceWorldData.Settings, T> setter) {
        appendSetting(builder, name, type, parser, getter, setter, false);
    }

    public static <T> void appendSettingOptional(ArgumentBuilder<CommandSourceStack, ?> builder, String name, ArgumentType<?> type, BiFunction<CommandContext<CommandSourceStack>, String, T> parser, Function<ResourceWorldData.Settings, Optional<T>> getter, BiConsumer<ResourceWorldData.Settings, T> setter) {
        appendSetting(builder, name, type, parser, s -> getter.apply(s).orElse(null), setter, true);
    }

    public static <T> void appendSetting(ArgumentBuilder<CommandSourceStack, ?> builder, String name, ArgumentType<?> type, BiFunction<CommandContext<CommandSourceStack>, String, T> parser, Function<ResourceWorldData.Settings, T> getter, BiConsumer<ResourceWorldData.Settings, T> setter, boolean optional) {
        LiteralArgumentBuilder<CommandSourceStack> l = literal(name);
        if (optional) l.then(literal("clear").executes(ctx -> {//clear
            CommandSourceStack source = ctx.getSource();
            setter.accept(CommandHelper.getDataChecked(ctx).getSettings(), null);
            CommandHelper.sendMessage(source, "setting.set", name, null);
            WorldConfig.saveConfig();
            return 1;
        }));
        l.then(argument("value", type).executes(ctx -> {//set
                    T value = parser.apply(ctx, "value");
                    setter.accept(CommandHelper.getDataChecked(ctx).getSettings(), value);
                    CommandHelper.sendMessage(ctx.getSource(), "setting.set", name, String.valueOf(value));
                    WorldConfig.saveConfig();
                    return 1;
                }))
                .executes(ctx -> {//get
                    CommandHelper.sendMessage(ctx.getSource(), "setting.get", name, String.valueOf(getter.apply(CommandHelper.getDataChecked(ctx).getSettings())));
                    return 1;
                });
        builder.then(l);
    }
}
