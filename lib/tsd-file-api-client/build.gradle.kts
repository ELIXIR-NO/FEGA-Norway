plugins {
    id("java-library")
    id("io.freefair.lombok") version "8.6"
    id("formatting-conventions")
}

group = "elixir.no"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.google.code.gson:gson:2.8.9")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.12")

    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// TODO: Configure the publishing settings for distributing the library/application.
