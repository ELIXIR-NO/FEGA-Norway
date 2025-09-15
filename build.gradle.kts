plugins {
    id("java")
    id("formatting-conventions")
    id("changelog")
}

group = "no.elixir"

subprojects {
    plugins.apply("changelog")
}

tasks.wrapper {
    gradleVersion = "8.10"
}
