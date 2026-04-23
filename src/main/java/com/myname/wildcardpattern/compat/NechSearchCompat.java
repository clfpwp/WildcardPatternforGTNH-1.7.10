package com.myname.wildcardpattern.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import cpw.mods.fml.common.Loader;

public final class NechSearchCompat {

    private static Boolean available;
    private static Object api;
    private static Method contains;

    private NechSearchCompat() {}

    public static boolean matches(String text, String search) {
        String needle = normalize(search);
        if (needle.isEmpty()) {
            return true;
        }
        String haystack = normalize(text);
        if (haystack.contains(needle)) {
            return true;
        }
        return containsWithNech(text, search) || containsWithNech(haystack, needle);
    }

    private static boolean containsWithNech(String text, String search) {
        if (!isAvailable() || text == null || search == null) {
            return false;
        }
        try {
            Object result = contains.invoke(api, text, search);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            available = Boolean.FALSE;
            return false;
        }
    }

    private static boolean isAvailable() {
        if (available != null) {
            return available.booleanValue();
        }
        if (!Loader.isModLoaded("nech")) {
            available = Boolean.FALSE;
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("com.asdflj.nech.API");
            Field instance = apiClass.getField("INSTANCE");
            api = instance.get(null);
            contains = apiClass.getMethod("contains", String.class, CharSequence.class);
            available = Boolean.TRUE;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            available = Boolean.FALSE;
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
