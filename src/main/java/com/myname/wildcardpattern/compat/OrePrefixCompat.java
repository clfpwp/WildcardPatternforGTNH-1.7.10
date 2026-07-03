package com.myname.wildcardpattern.compat;

import gregtech.api.enums.OrePrefixes;

public final class OrePrefixCompat {

    private OrePrefixCompat() {}

    public static String getPrefixName(OrePrefixes prefix) {
        if (prefix == null) {
            return "";
        }
        try {
            return (String) prefix.getClass().getMethod("getName").invoke(prefix);
        } catch (Exception ignored) {}
        try {
            return (String) prefix.getClass().getMethod("name").invoke(prefix);
        } catch (Exception ignored) {}
        return prefix.toString();
    }
}
