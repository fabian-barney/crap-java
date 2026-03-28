import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    `java-gradle-plugin`
}

group = "media.barney"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val coreJar = layout.projectDirectory.file("../core/target/crap4java-core-0.1.0-SNAPSHOT.jar")

val verifyCoreJar = tasks.register("verifyCoreJar") {
    doLast {
        if (!coreJar.asFile.exists()) {
            throw GradleException(
                "Missing ${coreJar.asFile}. Run `mvn -pl core -am package` from the repository root first."
            )
        }
    }
}

dependencies {
    implementation(files(coreJar))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    dependsOn(verifyCoreJar)
    useJUnitPlatform()
}

tasks.named("pluginUnderTestMetadata") {
    dependsOn(verifyCoreJar)
}

tasks.named<Jar>("jar") {
    dependsOn(verifyCoreJar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(coreJar))
}

gradlePlugin {
    plugins {
        create("crap4java") {
            id = "media.barney.crap4java"
            implementationClass = "media.barney.crap4java.gradle.Crap4JavaGradlePlugin"
            displayName = "crap4java Gradle Plugin"
            description = "Registers the crap4javaCheck verification task for Gradle Java projects."
        }
    }
}
