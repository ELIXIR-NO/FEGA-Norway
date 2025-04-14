plugins {
    id("java-library")
    id("io.freefair.lombok") version "8.13.1"
    id("formatting-conventions")
    id("maven-publish")
}

group = "elixir.no"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.38")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("com.google.code.gson:gson:2.13.0")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.6")

    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
            name = "fega-norway-tsd-file-api-client"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
