import org.gradle.internal.impldep.org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace
import sun.jvmstat.monitor.MonitoredVmUtil.mainClass

plugins {
    id("java")
    id("application")
    id("com.diffplug.spotless") version "8.1.0"
}

group = "org.nkcoder"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.nkcoder.Main")
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.1.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.23")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// spotless configuration for code formatting
spotless {
    java {
        importOrder()
        removeUnusedImports()

        // Choose one formatters: google or palantir
        palantirJavaFormat().formatJavadoc(true)
        formatAnnotations()
        trimTrailingWhitespace()
        leadingTabsToSpaces(2)
        endWithNewline()

        target("src/**/*.java")
    }
}

tasks.test {
    useJUnitPlatform()
}