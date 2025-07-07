plugins {
    id("java")
    id("maven-publish")
    id("io.freefair.lombok") version "8.14"
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
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.auth0:jwks-rsa:0.22.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.1")
    implementation("org.slf4j:slf4j-jdk14:2.0.17")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("com.squareup.okhttp3:okhttp:5.0.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    testImplementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0")
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
