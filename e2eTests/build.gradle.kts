plugins {
    id("java")
    id("formatting-conventions")
}

group = "no.elixir.fega"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// Start setup scripts.

tasks.register<Exec>("make-executable") {
    commandLine("chmod", "+x", "./setup.sh")
}

tasks.register<Exec>("cleanup") {
    dependsOn("make-executable")
    commandLine("sh", "-c", "./setup.sh clean")
}

tasks.register<Exec>("initialize") {
    dependsOn("cleanup")
    commandLine("sh", "-c", "./setup.sh init")
}

tasks.register<Exec>("generate-certs") {
    dependsOn("initialize")
    commandLine("sh", "-c", "./setup.sh generate_certs")
}

tasks.register<Exec>("apply-configs") {
    dependsOn("generate-certs")
    commandLine("sh", "-c", "./setup.sh apply_configs")
}

tasks.register<Exec>("start-docker-containers") {
    dependsOn("apply-configs")
    commandLine("docker-compose", "up", "-d")
}

tasks.register<Exec>("stop-docker-containers") {
    shouldRunAfter("test-e2e")
    commandLine("docker-compose", "down")
}

// End setup scripts.

tasks.register<Test>("test-e2e") {
    group = "verification"
    useJUnitPlatform()
    dependsOn("start-docker-containers")
    finalizedBy("stop-docker-containers")
}
