plugins {
    id("springboot-conventions")
}

group = "no.elixir"
version = "1.0-SNAPSHOT"

dependencies {
    runtimeOnly("com.h2database:h2")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
}

tasks.test {
    environment("DURABLE_FILE_IMPORT", "./tmp/")
}
