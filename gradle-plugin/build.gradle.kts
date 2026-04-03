import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `java-gradle-plugin`
    jacoco
    `maven-publish`
}

group = "media.barney"
version = "0.2.0"

repositories {
    mavenCentral()
}

jacoco {
    toolVersion = "0.8.13"
}

val projectVersion = version.toString()
val coreJar = layout.projectDirectory.file("../core/target/crap-java-core-${projectVersion}.jar")
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

tasks.withType<JavaCompile>().configureEach {
    dependsOn(verifyCoreJar)
    options.release.set(17)
}

dependencies {
    implementation(files(coreJar.asFile))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    dependsOn(verifyCoreJar)
    useJUnitPlatform()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named<Test>("test"))
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(false)
    }
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
            url = uri("https://maven.pkg.github.com/fabian-barney/crap-java")
            credentials {
                username = githubActor.orNull
                password = githubToken.orNull
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("crap-java Gradle Plugin")
            description.set("Gradle plugin exposing the crap-java-check verification task.")
            url.set("https://github.com/fabian-barney/crap-java")
        }
    }
}

gradlePlugin {
    plugins {
        create("crap-java") {
            id = "media.barney.crap-java"
            implementationClass = "media.barney.crapjava.gradle.CrapJavaGradlePlugin"
            displayName = "crap-java Gradle Plugin"
            description = "Registers the crap-java-check verification task for Gradle Java projects."
        }
    }
}
