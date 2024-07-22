plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.6"
    id("formatting-conventions")
}

group = "no.elixir"
version = "3.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("commons-cli:commons-cli:1.8.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("com.rfksystems:blake2b:2.0.0")
    implementation("at.favre.lib:bkdf:0.6.0")
    implementation("com.lambdaworks:scrypt:1.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
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
