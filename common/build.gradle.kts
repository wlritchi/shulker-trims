// Common module: platform-agnostic data models and logic
// No Minecraft dependencies here - just pure Java

dependencies {
    // Annotations for null safety (optional)
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Testing dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
