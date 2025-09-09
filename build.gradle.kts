plugins {
    id("java")
    id("formatting-conventions")
    id("changelog")
}

group = "no.elixir"

repositories { mavenCentral() }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test { useJUnitPlatform() }

subprojects {
    plugins.apply("changelog")
}

tasks.wrapper {
    gradleVersion = "8.10"
}
