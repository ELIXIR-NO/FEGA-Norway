plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.4"
    id("formatting-conventions")
}

group = "no.elixir"
version = "3.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.apache.commons:commons-lang3:3.13.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("commons-cli:commons-cli:1.6.0")
    implementation("commons-io:commons-io:2.15.0")
    implementation("com.rfksystems:blake2b:2.0.0")
    implementation("at.favre.lib:bkdf:0.6.0")
    implementation("com.lambdaworks:scrypt:1.4.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

extraJavaModuleInfo {
    automaticModule("bcrypt-0.10.2.jar", "bcrypt")
    automaticModule("bkdf-0.6.0.jar", "bkdf")
    automaticModule("scrypt-1.4.0.jar", "scrypt")
    // module("bcrypt-0.10.2.jar", "bcrypt", "0.10.2") {}

    // module("scrypt-1.4.0.jar", "scrypt","1.4.0") {}
    // module("bkdf-0.6.0.jar", "bkdf", "0.6.0") {}
}
tasks.test {
    useJUnitPlatform()
}
