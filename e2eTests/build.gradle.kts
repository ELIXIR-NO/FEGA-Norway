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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.rabbitmq:amqp-client:5.20.0")
    testImplementation("com.konghq:unirest-java:3.14.5") // FIXME ?
    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation("com.auth0:java-jwt:4.4.0") // FIXME: io.jsonwebtoken
    testImplementation("commons-io:commons-io:2.15.1")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.12")
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

tasks.register<Exec>("stop-docker-containers") {
    commandLine("docker", "compose", "down")
}

tasks.test {
    useJUnitPlatform()
//    dependsOn("start-docker-containers")
//    finalizedBy("stop-docker-containers")
    // Ensure this test runs only after all other
    // test tasks are completed
    mustRunAfter(
        ":lib:crypt4gh:test",
        ":lib:clearinghouse:test",
        ":lib:tsd-file-api-client:test",
        ":services:tsd-api-mock:test",
        ":services:mq-interceptor:test",
        ":services:localega-tsd-proxy:test"
    )
}
