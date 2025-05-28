import java.util.Base64

plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.14"
    id("formatting-conventions")
    id("maven-publish")
    id("signing")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jreleaser") version "1.18.0"
}

group = "no.elixir"

// The target Java version can be overriden on the command-line with the argument "-PjavaVersion=<version>"
// A JDK of this version must be available on your system
val javaVersion = (project.findProperty("javaVersion") as String?)?.toInt() ?: 21

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-codec:commons-codec:1.19.0")
    implementation("commons-cli:commons-cli:1.10.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("com.rfksystems:blake2b:2.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    // Make javadoc and sources JARs
    withJavadocJar()
    withSourcesJar()
}

// Note: the project version should be set with the argument "-Pversion=<version>" when building
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Elixir Norway",
            "Main-Class" to "no.elixir.crypt4gh.app.Main"
        )
    }
}

// Create fat JAR which includes all external dependencies
tasks.shadowJar {
    archiveBaseName.set("crypt4gh-tool")
    archiveClassifier.set("tool")
    archiveVersion.set(
        if (project.version == "unspecified") "" else project.version.toString()
    )
    archiveFileName.set(
        if (project.version == "unspecified") "crypt4gh-tool.jar" else "crypt4gh-tool-${project.version}.jar"
    )
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

/**
 * Helper functions to create shared POM configurations with extra metadata,
 * including organization, license, SCM, and the names of developers
 */
fun MavenPom.setupPomDetails() {
    name.set("Crypt4GH")
    description.set("Crypt4GH standard implementation")
    properties.put("maven.compiler.source", javaVersion.toString())
    properties.put("maven.compiler.target", javaVersion.toString())
    properties.put("maven.compiler.release", javaVersion.toString())
    url.set("https://github.com/ELIXIR-NO/FEGA-Norway/tree/main/lib/crypt4gh")
    organization {
        name.set("Elixir Norway")
        url.set("https://elixir.no")
    }
    licenses {
        license {
            name.set("MIT License")
            url.set("https://opensource.org/license/MIT")
            distribution.set("repo")
        }
    }
    scm {
        connection.set("scm:git:git://github.com/ELIXIR-NO/FEGA-Norway.git")
        developerConnection.set("scm:git:ssh://github.com:ELIXIR-NO/FEGA-Norway.git")
        url.set("https://github.com/ELIXIR-NO/FEGA-Norway")
    }
    issueManagement {
        system.set("GitHub")
        url.set("https://github.com/ELIXIR-NO/FEGA-Norway/issues")
    }
    configureDevelopers()
}

/**
 * Adds a <developers> block to the Maven POM XML.
 */
fun MavenPom.configureDevelopers() {
    withXml {
        val root = asNode()

        // Remove any existing <developers> section just in case
        root.children().removeIf { (it as? groovy.util.Node)?.name() == "developers" }

        // Create <developers> block
        val developers = root.appendNode("developers")

        developers.addDeveloper("dtitov", "Dmytro Titov", listOf("Lead Developer (emeritus)"))
        developers.addDeveloper("kjetilkl", "Kjetil Klepper", listOf("Developer", "Maintainer"))
        developers.addDeveloper("a-ghanem", "Ahmed Ghanem", listOf("Maintainer (emeritus)"))
        developers.addDeveloper("lyytinen", "Jussi Lyytinen", listOf("Contributor"))
        developers.addDeveloper("brainstorm", "Roman Valls Guimera", listOf("Contributor"))
    }
}

/**
 * Appends a <developer> entry with roles to a <developers> XML node.
 *
 * @param id
 * @param name
 * @param roles
 */
fun groovy.util.Node.addDeveloper(
    id: String,
    name: String,
    roles: List<String>
) {
    val dev = appendNode("developer")
    dev.appendNode("id", id)
    dev.appendNode("name", name)
    val rolesNode = dev.appendNode("roles")
    roles.forEach { rolesNode.appendNode("role", it) }
}

/**
 * Manually adds the dependencies listed in this Gradle-file
 * to the generated pom.xml-file of a MavenPublication.
 * This must be used if a publication is created by picking
 * individual artifacts rather than using 'from(components["java"])'
 */
fun MavenPom.includeDependencies() {
    withXml {
        val dependenciesNode = asNode().appendNode("dependencies")
        configurations.runtimeClasspath.get()
            .allDependencies
            .filterIsInstance<ModuleDependency>()
            .filter { it.group != null && it.version != null }
            .forEach {
                val depNode = dependenciesNode.appendNode("dependency")
                depNode.appendNode("groupId", it.group)
                depNode.appendNode("artifactId", it.name)
                depNode.appendNode("version", it.version)
                depNode.appendNode("scope", "runtime")
            }
    }
}

publishing {
    publications {
        // Publish everything, including slim and fat JAR, docs, sources and signature files (for publication to Maven Central)
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                setupPomDetails()
            }
        }

        // Publish only the regular JAR without dependencies, but list the dependencies in the pom-file.
        // This can be imported as a library in other projects.
        create<MavenPublication>("library") {
            artifactId = "crypt4gh"
            artifact(tasks["jar"])
            pom {
                setupPomDetails()
                includeDependencies() // this must be included manually when picking individual artifacts
            }
        }

        // Publish a fat JAR that includes all the external dependencies (exclude dependencies from pom-file).
        // This can be used as a stand-alone tool on the command-line.
        // Note that this tool is published with a different artifact name
        create<MavenPublication>("tool") {
            artifactId = "crypt4gh-tool"
            artifact(tasks.shadowJar.get()) {
                // Fat JAR
                classifier = ""
                builtBy(tasks.shadowJar)
            }
            pom {
                setupPomDetails()
                // the pom-file for the fat Jar does not include any dependencies
            }
        }
    }

    repositories {
        maven {
            name = "fega-norway-crypt4gh"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }

        maven { // this currently only works for snapshots
            name = "MavenCentral"
            val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
            url = uri(
                if (isSnapshot) "https://central.sonatype.com/repository/maven-snapshots/"
                else "https://central.sonatype.com/api/v1/publisher/"
            )
            credentials {
                username = System.getenv("MAVEN_CENTRAL_TOKEN_USER") ?: ""
                password = System.getenv("MAVEN_CENTRAL_TOKEN_PASSWORD") ?: ""
            }
        }
        // Publish all files required by Maven Central to a local staging directory under lib/crypt4gh/build/
	// These can later be pushed to Maven Central by JReleaser
        maven {
            name = "localStaging"
	    url = layout.buildDirectory.get().asFile.resolve("jreleaser/staging-deploy").toURI()
        }
    }
}


signing {
    // the signing key should be supplied in Base64 encoded format
    val base64Key = System.getenv("SIGNING_KEY_BASE64") ?: findProperty("signing.key") as String?
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: findProperty("signing.password") as String?

    if (!base64Key.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        val signingKey = String(Base64.getDecoder().decode(base64Key))
        useInMemoryPgpKeys(signingKey, signingPassword)
        isRequired = true
        sign(publishing.publications["mavenJava"])
    } else {
        logger.warn("Signing key or password not found. Skipping signing.")
    }
}

jreleaser { // this is not working yet
   signing { // signing will be performed by the signing-block above
      setActive("NEVER")
   }
   deploy {
      maven {
         mavenCentral {
            create("sonatype") {
               setActive("RELEASE")
               sign.set(false) // the artifacts should already have been signed
               applyMavenCentralRules.set(true)
               url.set("https://central.sonatype.com/api/v1/publisher")
               username.set(System.getenv("MAVEN_CENTRAL_TOKEN_USER") ?: "")
               password.set(System.getenv("MAVEN_CENTRAL_TOKEN_PASSWORD") ?: "")
               stagingRepository(layout.buildDirectory.get().asFile.resolve("jreleaser/staging-deploy").absolutePath)
            }
         }
      }
   }
}

// Block publishing if the version number is not specified
// Do not publish SHAPSHOTs to GitHub Packages
tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        val versionStr = project.version.toString()
        if (versionStr == "unspecified") {
            throw GradleException("Cannot publish with an unspecified version. Use argument: -Pversion=X.Y.Z")
        }

        val isSnapshot = versionStr.endsWith("-SNAPSHOT")
        val repoName = repository.name

        when {
            repoName == "fega-norway-crypt4gh" && isSnapshot -> {
                logger.lifecycle("Skipping SNAPSHOT publishing to GitHub Packages")
                onlyIf { false }
            }
            else -> logger.lifecycle("Publishing ${project.name} $versionStr to $repoName")
        }
    }
}

tasks.withType<PublishToMavenLocal>().configureEach {
    doFirst {
        if (project.version.toString() == "unspecified") {
            throw GradleException("Cannot publish to MavenLocal with an unspecified version. Use argument: -Pversion=X.Y.Z")
        }
    }
}
