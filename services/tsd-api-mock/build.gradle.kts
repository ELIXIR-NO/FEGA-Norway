plugins {
    id("java")
    id("springboot-conventions")
    id("jsonwebtoken")
}

group = "no.elixir"
version = "1.0-SNAPSHOT"

dependencies {
    runtimeOnly("com.h2database:h2")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
