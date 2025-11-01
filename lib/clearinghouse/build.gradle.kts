import java.util.Base64

plugins {
    id("java")
    id("maven-publish")
    id("signing")
    id("io.freefair.lombok") version "9.0.0"
    id("formatting-conventions")
}

group = "no.elixir"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.apache.commons:commons-lang3:3.19.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    implementation("org.slf4j:slf4j-jdk14:2.0.17")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.bouncycastle:bcprov-jdk15to18:1.82")
    testImplementation("org.bouncycastle:bcpkix-jdk15to18:1.82")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Clearinghouse")
                description.set("GA4GH passports validation and parsing")
                url.set("https://github.com/ELIXIR-NO/FEGA-Norway/tree/main/lib/clearinghouse")
                organization {
                    name.set("Elixir Norway")
                    url.set("https://elixir.no")
                }
                developers {
                    developer {
                        id.set("dtitov")
                        name.set("Dmytro Titov")
                    }
                    developer {
                        id.set("Parisa68")
                        name.set("Parisa")
                    }
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/MIT")
                        distribution.set("repo")
                    }
                }
                scm {
                    url.set("https://github.com/ELIXIR-NO/FEGA-Norway")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/ELIXIR-NO/FEGA-Norway/issues")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GithubRegistry"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }

        maven {
            // this currently only works for snapshots
            name = "MavenCentral"
            val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
            url = uri(
                if (isSnapshot) {
                    "https://central.sonatype.com/repository/maven-snapshots/"
                } else {
                    "https://central.sonatype.com/api/v1/publisher/"
                }
            )
            credentials {
                username = System.getenv("MAVEN_CENTRAL_TOKEN_USER") ?: ""
                password = System.getenv("MAVEN_CENTRAL_TOKEN_PASSWORD") ?: ""
            }
        }

        // Publish all files required by Maven Central to a local staging directory under lib/clearinghouse/build/
        // These can later be pushed to Maven Central by JReleaser
        maven {
            name = "localStaging"
            url = layout.buildDirectory.get()
                .asFile
                .resolve("jreleaser/staging-deploy")
                .toURI()
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
            repoName == "GithubRegistry" && isSnapshot -> {
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
            throw GradleException(
                "Cannot publish to MavenLocal with an unspecified version. Use argument: -Pversion=X.Y.Z"
            )
        }
    }
}
