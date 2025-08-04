plugins {
    id("java-library")
    id("io.freefair.lombok") version "8.14"
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

    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("com.google.code.gson:gson:2.13.1")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.6")

    api("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
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
