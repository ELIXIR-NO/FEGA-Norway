plugins {
    id("springboot-conventions")
}

group = "no.elixir"
version = "1.0-SNAPSHOT"

dependencies {
    runtimeOnly("com.h2database:h2")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
}
