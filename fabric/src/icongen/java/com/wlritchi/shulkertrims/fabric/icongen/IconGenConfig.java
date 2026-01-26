package com.wlritchi.shulkertrims.fabric.icongen;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Parses command-line arguments (passed as system properties) for icon generation.
 */
public class IconGenConfig {

    private static final String PREFIX = "shulker_trims.icongen.";

    private final List<String> colors;
    private final List<String> patterns;
    private final List<String> materials;
    private final Path outputDir;
    private final int size;

    public IconGenConfig() {
        this.colors = parseList("color", "purple");
        this.patterns = parseList("pattern", "sentry");
        this.materials = parseList("material", "gold");
        this.outputDir = Paths.get(System.getProperty(PREFIX + "output", "build/icons"));
        this.size = Integer.parseInt(System.getProperty(PREFIX + "size", "512"));
    }

    private static List<String> parseList(String key, String defaultValue) {
        String value = System.getProperty(PREFIX + key, defaultValue);
        return Arrays.asList(value.split(","));
    }

    public List<String> getColors() {
        return colors;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getMaterials() {
        return materials;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public int getSize() {
        return size;
    }

    /**
     * Returns total number of icons to generate (Cartesian product).
     */
    public int getTotalCombinations() {
        return colors.size() * patterns.size() * materials.size();
    }
}
