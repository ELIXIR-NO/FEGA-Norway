plugins {
    id("java")
    id("maven-publish")
    id("io.freefair.lombok") version "8.13.1"
    id("formatting-conventions")
}

group = "no.elixir"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.commons.collections4)
    implementation(libs.commons.lang3)
    implementation(libs.gson)
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation(libs.slf4j.jdk14)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation("org.bouncycastle:bcprov-jdk15to18:1.80")
    testImplementation("org.bouncycastle:bcpkix-jdk15to18:1.80")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.okhttp)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Clearinghouse")
                description.set("GA4GH passports validation and parsing")
                url.set("https://github.com/ELIXIR-NO/FEGA-Norway/tree/main/lib/clearinghouse")
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
            name = "fega-norway-clearinghouse"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
