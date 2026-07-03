package com.myname.wildcardpattern.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import gregtech.api.enums.OrePrefixes;

public final class GTCompat {

    private static volatile OrePrefixes[] orePrefixes;

    private GTCompat() {}

    public static OrePrefixes[] orePrefixes() {
        OrePrefixes[] cached = orePrefixes;
        if (cached != null) {
            return cached.clone();
        }

        OrePrefixes[] resolved = resolveOrePrefixes();
        orePrefixes = resolved;
        return resolved.clone();
    }

    private static OrePrefixes[] resolveOrePrefixes() {
        OrePrefixes[] prefixes = invokeValuesMethod();
        if (prefixes.length > 0) {
            return prefixes;
        }
        prefixes = readValuesField();
        if (prefixes.length > 0) {
            return prefixes;
        }
        return new OrePrefixes[0];
    }

    private static OrePrefixes[] invokeValuesMethod() {
        try {
            Method method = OrePrefixes.class.getMethod("values");
            Object value = method.invoke(null);
            if (value instanceof OrePrefixes[] prefixes) {
                return prefixes.clone();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // GTNH 2.9 changed OrePrefixes from an enum-like API to a class with VALUES.
        }
        return new OrePrefixes[0];
    }

    private static OrePrefixes[] readValuesField() {
        try {
            Field field = OrePrefixes.class.getField("VALUES");
            Object value = field.get(null);
            if (value instanceof OrePrefixes[] prefixes) {
                return prefixes.clone();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Older GT versions expose values() instead.
        }
        return new OrePrefixes[0];
    }
}
