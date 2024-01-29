plugins {
    id("java")
    id("maven-publish")
    id("io.freefair.lombok") version "8.4"
    id("formatting-conventions")
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.slf4j:slf4j-jdk14:2.0.11")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.bouncycastle:bcprov-jdk15to18:1.77")
    testImplementation("org.bouncycastle:bcpkix-jdk15to18:1.77")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.4")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
}
