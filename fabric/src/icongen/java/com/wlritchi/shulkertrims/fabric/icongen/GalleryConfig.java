package com.wlritchi.shulkertrims.fabric.icongen;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Configuration for gallery image generation. */
public class GalleryConfig {

  private static final String PREFIX = "shulker_trims.gallery.";

  private final Path outputPath;
  private final int width;
  private final int height;

  public GalleryConfig() {
    this.outputPath = Paths.get(System.getProperty(PREFIX + "output", "build/gallery/gallery.png"));
    this.width = Integer.parseInt(System.getProperty(PREFIX + "width", "1920"));
    this.height = Integer.parseInt(System.getProperty(PREFIX + "height", "1080"));
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
