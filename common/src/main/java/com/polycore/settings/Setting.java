package com.polycore.settings;

import com.polycore.config.PolyConfig;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.nbt.CompoundTag;

/**
 * One editable server setting, backed by a field of {@link PolyConfig.Data}.
 */
public final class Setting {
    public enum Type { BOOL, INT }

    public final String key;
    public final String category;
    public final String label;
    public final String description;
    public final Type type;
    public final int min;
    public final IntSupplier max;
    /** Text shown instead of the number for 0 (e.g. "Auto"), or null. */
    public final String specialZero;
    /** Unit shown after the number ("ms", "chunks"...), or empty. */
    public final String unit;

    private final BooleanSupplier boolGetter;
    private final Consumer<Boolean> boolSetter;
    private final IntSupplier intGetter;
    private final IntConsumer intSetter;

    private Setting(String key, String category, String label, String description, Type type, int min, IntSupplier max,
                    String specialZero, String unit, BooleanSupplier boolGetter, Consumer<Boolean> boolSetter,
                    IntSupplier intGetter, IntConsumer intSetter) {
        this.key = key;
        this.category = category;
        this.label = label;
        this.description = description;
        this.type = type;
        this.min = min;
        this.max = max;
        this.specialZero = specialZero;
        this.unit = unit;
        this.boolGetter = boolGetter;
        this.boolSetter = boolSetter;
        this.intGetter = intGetter;
        this.intSetter = intSetter;
    }

    public static Setting bool(String key, String category, String label, String description,
                               BooleanSupplier getter, Consumer<Boolean> setter) {
        return new Setting(key, category, label, description, Type.BOOL, 0, () -> 1, null, "", getter, setter, null, null);
    }

    public static Setting integer(String key, String category, String label, String description, int min, IntSupplier max,
                                  String specialZero, String unit, IntSupplier getter, IntConsumer setter) {
        return new Setting(key, category, label, description, Type.INT, min, max, specialZero, unit, null, null, getter, setter);
    }

    public boolean getBool() {
        return boolGetter != null && boolGetter.getAsBoolean();
    }

    public int getInt() {
        return intGetter == null ? 0 : intGetter.getAsInt();
    }

    /** Sets the value from text ("true", "off", "12"...), clamps it and saves the config. Returns an error or null. */
    public String set(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (type == Type.BOOL) {
            boolean b;
            if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on")) b = true;
            else if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("off")) b = false;
            else return "Expected true or false, got \"" + raw + "\"";
            boolSetter.accept(b);
        } else {
            int i;
            try {
                i = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return "Not a whole number: \"" + raw + "\"";
            }
            int max = this.max.getAsInt();
            if (i < min || i > max) return label + " must be between " + min + " and " + max + ".";
            intSetter.accept(i);
        }
        PolyConfig.save();
        return null;
    }

    public String displayValue() {
        if (type == Type.BOOL) return getBool() ? "On" : "Off";
        int v = getInt();
        if (v == 0 && specialZero != null) return specialZero;
        return unit.isEmpty() ? Integer.toString(v) : v + " " + unit;
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("key", key);
        t.putString("category", category);
        t.putString("label", label);
        t.putString("desc", description);
        t.putString("type", type.name());
        t.putInt("min", min);
        t.putInt("max", max.getAsInt());
        t.putString("unit", unit);
        if (specialZero != null) t.putString("zero", specialZero);
        if (type == Type.BOOL) t.putBoolean("value", getBool());
        else t.putInt("value", getInt());
        return t;
    }
}
