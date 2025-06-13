plugins {
    id("java")
    id("formatting-conventions")
}

group = "no.elixir"

repositories { mavenCentral() }

tasks.test { useJUnitPlatform() }

tasks.wrapper {
    gradleVersion = "8.10"
}
