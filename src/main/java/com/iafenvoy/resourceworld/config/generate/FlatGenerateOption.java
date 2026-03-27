package com.iafenvoy.resourceworld.config.generate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.resourceworld.ResourceWorld;
import com.iafenvoy.resourceworld.util.RLUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
//? >=1.20.5 {
import com.mojang.serialization.MapCodec;
//?}
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.lang.reflect.Field;
import java.util.Optional;

public record FlatGenerateOption(String preset) implements GenerateOption {
    public static final /*? >=1.20.5 {*/ MapCodec/*?} else {*//*Codec*//*?}*/<FlatGenerateOption> CODEC = RecordCodecBuilder./*? >=1.20.5 {*/mapCodec/*?} else {*//*create*//*?}*/(i -> i.group(
            Codec.STRING.fieldOf("preset").forGetter(FlatGenerateOption::preset)
    ).apply(i, FlatGenerateOption::new));

    @Override
    public /*? >=1.20.5 {*/MapCodec/*?} else {*//*Codec*//*?}*/<? extends GenerateOption> codec() {
        return CODEC;
    }

    @Override
    public LevelStem createStem(RegistryAccess registries) {
        return new LevelStem(registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD), new FlatLevelSource(createFromString(registries, this.preset)));
    }

    public static FlatLevelGeneratorSettings createFromString(RegistryAccess registries, String preset) {
        HolderGetter<Biome> biomeGetter = registries.lookupOrThrow(Registries.BIOME);
        HolderGetter<StructureSet> structureGetter = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        HolderGetter<PlacedFeature> featureGetter = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        FlatLevelGeneratorSettings defaults = FlatLevelGeneratorSettings.getDefault(biomeGetter, structureGetter, featureGetter);
        FlatLevelGeneratorSettings parsed = decodeFromPresetString(preset, defaults);
        return parsed != null ? parsed : defaults;
    }

    private static FlatLevelGeneratorSettings decodeFromPresetString(String preset, FlatLevelGeneratorSettings defaults) {
        try {
            JsonElement json = presetToJson(preset);
            Codec<FlatLevelGeneratorSettings> codec = findCodec();
            if (codec == null) return defaults;
            Optional<FlatLevelGeneratorSettings> parsed = codec.parse(JsonOps.INSTANCE, json).resultOrPartial(ResourceWorld.LOGGER::error);
            return parsed.orElse(defaults);
        } catch (Exception e) {
            return defaults;
        }
    }

    private static JsonElement presetToJson(String preset) {
        String s = preset == null ? "" : preset.trim();
        if (s.startsWith("{")) return JsonParser.parseString(s);
        return legacyPresetToJson(s);
    }

    private static JsonElement legacyPresetToJson(String preset) {
        String[] parts = preset.split(";", -1);
        int idx = 0;
        if (parts.length > 0 && parts[0].trim().matches("\\d+")) idx = 1;

        String layersPart = parts.length > idx ? parts[idx].trim() : "";
        String biomePart = parts.length > idx + 1 ? parts[idx + 1].trim() : "minecraft:plains";

        JsonObject root = new JsonObject();
        root.add("structure_overrides", new JsonArray());
        root.add("layers", parseLegacyLayers(layersPart));
        root.add("biome", new JsonPrimitive(biomePart.isEmpty() ? "minecraft:plains" : biomePart));
        return root;
    }

    private static JsonArray parseLegacyLayers(String layersPart) {
        JsonArray layers = new JsonArray();
        if (layersPart == null || layersPart.isEmpty()) return layers;

        String[] tokens = layersPart.split(",");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;

            int height = 1;
            String blockId = t;
            int star = t.indexOf('*');
            if (star > 0) {
                String left = t.substring(0, star).trim();
                String right = t.substring(star + 1).trim();
                try {
                    height = Integer.parseInt(left);
                    blockId = right;
                } catch (NumberFormatException ignored) {
                    height = 1;
                    blockId = t;
                }
            }

            JsonObject layer = new JsonObject();
            layer.add("block", new JsonPrimitive(blockId));
            layer.add("height", new JsonPrimitive(height));
            layers.add(layer);
        }
        return layers;
    }

    @SuppressWarnings("unchecked")
    private static Codec<FlatLevelGeneratorSettings> findCodec() {
        for (String name : new String[]{"CODEC", "DIRECT_CODEC"}) {
            try {
                Field f = FlatLevelGeneratorSettings.class.getDeclaredField(name);
                f.setAccessible(true);
                Object value = f.get(null);
                if (value instanceof Codec<?> codec) return (Codec<FlatLevelGeneratorSettings>) codec;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public ResourceLocation getDimensionTypeId() {
        return RLUtil.id(this.preset);
    }
}
