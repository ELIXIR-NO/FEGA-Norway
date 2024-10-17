plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.10"
    id("formatting-conventions")
    id("maven-publish")
}
// fake changes
group = "no.elixir"
version = "3.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("com.rfksystems:blake2b:2.0.0")
    implementation("at.favre.lib:bkdf:0.6.0")
    implementation("com.lambdaworks:scrypt:1.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extraJavaModuleInfo {
    automaticModule("bcrypt-0.10.2.jar", "bcrypt")
    automaticModule("bkdf-0.6.0.jar", "bkdf")
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
