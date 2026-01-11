import groovy.json.JsonSlurper
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    id("fabric-loom")
}

// Task to extract shulker trim PNGs from BlockBench model file
abstract class ExtractShulkerTrims : DefaultTask() {
    @get:InputFile
    abstract val bbmodelFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val trimPatterns = listOf(
        "sentry", "vex", "wild", "coast", "dune", "wayfinder",
        "raiser", "shaper", "host", "ward", "silence", "tide",
        "snout", "rib", "eye", "spire", "flow", "bolt"
    )

    @TaskAction
    fun extract() {
        val bbmodel = bbmodelFile.get().asFile
        if (!bbmodel.exists()) {
            throw GradleException("BBModel file not found: ${bbmodel.absolutePath}")
        }

        val json = try {
            @Suppress("UNCHECKED_CAST")
            JsonSlurper().parse(bbmodel) as Map<String, Any>
        } catch (e: Exception) {
            throw GradleException("Failed to parse bbmodel: ${e.message}")
        }

        @Suppress("UNCHECKED_CAST")
        val textures = json["textures"] as? List<Map<String, Any>>
            ?: throw GradleException("No textures found in bbmodel")

        if (textures.isEmpty()) {
            throw GradleException("Textures array is empty in bbmodel")
        }

        @Suppress("UNCHECKED_CAST")
        val layers = textures[0]["layers"] as? List<Map<String, Any>>
            ?: throw GradleException("No layers found in bbmodel texture")

        val layersByName = layers.associateBy { it["name"] as String }
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        for (pattern in trimPatterns) {
            val layer = layersByName[pattern]
                ?: throw GradleException("Layer '$pattern' not found in bbmodel")

            val dataUrl = layer["data_url"] as? String
                ?: throw GradleException("No data_url for layer '$pattern'")

            val base64Data = dataUrl.substringAfter("base64,")
            val pngBytes = try {
                Base64.getDecoder().decode(base64Data)
            } catch (e: Exception) {
                throw GradleException("Failed to decode PNG data for layer '$pattern': ${e.message}")
            }

            // Read the image and pad to 64x64 if needed
            val sourceImage = try {
                ImageIO.read(ByteArrayInputStream(pngBytes))
            } catch (e: Exception) {
                throw GradleException("Failed to read PNG for layer '$pattern': ${e.message}")
            }

            val outputImage = if (sourceImage.width == 64 && sourceImage.height == 64) {
                sourceImage
            } else {
                // Create 64x64 canvas with transparency and draw source at origin
                val padded = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
                padded.createGraphics().apply {
                    drawImage(sourceImage, 0, 0, null)
                    dispose()
                }
                padded
            }

            val outFile = File(outDir, "$pattern.png")
            ImageIO.write(outputImage, "PNG", outFile)
            logger.info("Extracted $pattern.png (${sourceImage.width}x${sourceImage.height} -> 64x64)")
        }

        logger.lifecycle("Extracted ${trimPatterns.size} shulker trim textures")
    }
}

// Register and configure the extraction task
val generatedResources = layout.buildDirectory.dir("generated/resources/shulker-trims")

val extractShulkerTrims by tasks.registering(ExtractShulkerTrims::class) {
    bbmodelFile.set(rootProject.file("art/shulker-trims.bbmodel"))
    outputDir.set(generatedResources.map { it.dir("assets/shulker_trims/textures/trims/entity/shulker") })
}

// Add generated resources to source sets
sourceSets.main {
    resources.srcDir(generatedResources)
}

// Ensure extraction runs before resource processing
tasks.processResources {
    dependsOn(extractShulkerTrims)
    // Exclude reference textures (design aids, not for distribution)
    exclude("**/reference_*")
}

// Also ensure sources jar depends on extraction
tasks.named("sourcesJar") {
    dependsOn(extractShulkerTrims)
}

base {
    archivesName.set("${property("archives_base_name")}-fabric")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("shulker_trims") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "shulker_trims_test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

// Note: For headless client game tests on Linux, use:
//   xvfb-run -a ./gradlew :fabric:runClientGameTest
// This ensures consistent rendering between local and CI environments.

repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    implementation(project(":common"))
}

// Include common module classes in the fabric jar
tasks.jar {
    from(project(":common").sourceSets.main.get().output)
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    // Ensure common classes are included before remapping
    dependsOn(project(":common").tasks.named("classes"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// Don't run game tests as part of the standard build - they're slow.
// Use ./gradlew slowTest to run them explicitly.
afterEvaluate {
    tasks.named("test") {
        setDependsOn(dependsOn.filterNot { dep ->
            dep.toString().contains("runGameTest")
        })
    }

    // Disable network synchronization for client game tests to allow external server connections
    tasks.named<JavaExec>("runClientGameTest") {
        jvmArgs("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
    }
}
