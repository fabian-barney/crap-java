import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "media.barney"
version = "0.1.0"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val projectVersion = version.toString()
val coreJar = layout.projectDirectory.file("../core/target/crap4java-core-${projectVersion}.jar")
val githubActor = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR"))
val githubToken = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN"))

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

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/fabian-barney/crap4java")
            credentials {
                username = githubActor.orNull
                password = githubToken.orNull
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("crap4java Gradle Plugin")
            description.set("Gradle plugin exposing the crap4javaCheck verification task.")
            url.set("https://github.com/fabian-barney/crap4java")
        }
    }
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
