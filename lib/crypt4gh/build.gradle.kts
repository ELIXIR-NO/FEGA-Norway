plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.12.1"
    id("formatting-conventions")
    id("maven-publish")
}

group = "no.elixir"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("commons-io:commons-io:2.18.0")
    implementation("com.rfksystems:blake2b:2.0.0")
    implementation("com.lambdaworks:scrypt:1.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extraJavaModuleInfo {
    automaticModule("scrypt-1.4.0.jar", "scrypt")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "fega-norway-crypt4gh"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
