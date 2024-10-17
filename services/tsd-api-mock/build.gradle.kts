plugins {
    id("java")
    id("springboot-conventions")
    id("jsonwebtoken")
}
// fake changes
group = "no.elixir"
version = "2.0.0"

dependencies {
    runtimeOnly("com.h2database:h2")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    environment("DURABLE_FILE_IMPORT", "./tmp/")
}

// Build Docker image
tasks.register("buildDockerImage", Exec::class) {
    group = "build"
    dependsOn("bootJar")
    description = "Builds the Docker image for tsd-api-mock"
    commandLine("docker", "build", "-t", "tsd-api-mock", ".")
}
