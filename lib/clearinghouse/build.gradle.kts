plugins {
    id("java")
    id("maven-publish")
    id("io.freefair.lombok") version "8.6"
    id("formatting-conventions")
    id("jsonwebtoken")
    id("okhttp")
}

group = "no.elixir"
version = "2.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")
    testImplementation("org.bouncycastle:bcpkix-jdk15to18:1.78.1")
}
