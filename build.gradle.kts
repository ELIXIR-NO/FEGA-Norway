plugins {
    id("java")
    id("formatting-conventions")
}

group = "no.elixir"

version = "1.0.2"

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

tasks.wrapper {
    gradleVersion = "8.10"
}
