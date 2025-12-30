package com.wlritchi.shulkertrims.common;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a trim applied to a shulker box.
 * Platform-agnostic data model used by both Fabric and Bukkit implementations.
 */
public record ShulkerTrim(
    @NotNull String pattern,
    @NotNull String material
) {
    public static final String NBT_KEY = "shulker_trims:trim";
    public static final String NBT_PATTERN_KEY = "pattern";
    public static final String NBT_MATERIAL_KEY = "material";

    public ShulkerTrim {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
    }

    /**
     * Validates that the pattern is a valid Minecraft identifier.
     */
    public boolean hasValidPattern() {
        return isValidIdentifier(pattern);
    }

    /**
     * Validates that the material is a valid Minecraft identifier.
     */
    public boolean hasValidMaterial() {
        return isValidIdentifier(material);
    }

    /**
     * Checks if both pattern and material are valid identifiers.
     */
    public boolean isValid() {
        return hasValidPattern() && hasValidMaterial();
    }

    /**
     * Simple validation for Minecraft resource identifiers (namespace:path format).
     */
    private static boolean isValidIdentifier(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        int colonIndex = id.indexOf(':');
        if (colonIndex < 1 || colonIndex == id.length() - 1) {
            return false;
        }
        String namespace = id.substring(0, colonIndex);
        String path = id.substring(colonIndex + 1);
        return isValidNamespace(namespace) && isValidPath(path);
    }

    private static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
