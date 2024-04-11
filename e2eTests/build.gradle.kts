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
    testImplementation("com.rabbitmq:amqp-client:5.20.0")
    testImplementation("com.konghq:unirest-java:3.14.5") // FIXME ?
    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation("com.auth0:java-jwt:4.4.0") // FIXME: io.jsonwebtoken
    testImplementation("commons-io:commons-io:2.15.1")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.12")
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
    commandLine("docker", "compose", "up", "-d")
}

tasks.register<Exec>("run-tests") {
    dependsOn("start-docker-containers")
}

tasks.register<Exec>("stop-docker-containers") {
    shouldRunAfter("run-tests")
    commandLine("docker", "compose", "down")
}

// End setup scripts.

tasks.named<Test>("test") {
    group = "verification"
    useJUnitPlatform()
    dependsOn("start-docker-containers")
    finalizedBy("stop-docker-containers")
}
