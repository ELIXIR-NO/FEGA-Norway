plugins {
    id("java")
    id("formatting-conventions")
}

group = "no.elixir.fega"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.rabbitmq:amqp-client:5.20.0")
    testImplementation("com.konghq:unirest-java:3.14.5")
    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation("com.auth0:java-jwt:4.4.0") // FIXME: io.jsonwebtoken
    testImplementation("commons-io:commons-io:2.15.1")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.12")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    implementation("io.github.cdimascio:java-dotenv:5.2.2")
}

// Start setup scripts.

tasks.register<Exec>("make-executable") {
    commandLine("chmod", "+x", "./scripts/bootstrap.sh")
}

tasks.register<Exec>("cleanup") {
    dependsOn("make-executable")
    commandLine("../gradlew", "clean")
}

tasks.register<Exec>("assemble-binaries") {
    dependsOn("cleanup")
    commandLine(
        "../gradlew",
        ":cli:lega-commander:build",
        ":lib:crypt4gh:build",
        ":lib:clearinghouse:build",
        ":lib:tsd-file-api-client:build",
        ":services:cega-mock:build",
        ":services:tsd-api-mock:build",
        ":services:mq-interceptor:build",
        ":services:localega-tsd-proxy:build",
        "-x",
        "test",
        "--parallel"
    )
}

tasks.register<Exec>("check-requirements") {
    dependsOn("assemble-binaries")
    commandLine("sh", "-c", "./scripts/bootstrap.sh apply_configs")
}

tasks.register<Exec>("apply-configs") {
    dependsOn("check-requirements")
    commandLine("sh", "-c", "./scripts/bootstrap.sh check_requirements")
}

tasks.register<Exec>("start-docker-containers") {
    dependsOn("apply-configs")
    commandLine("docker", "compose", "up", "--pull", "always", "--build", "-d")
}

tasks.register<Exec>("stop-docker-containers") {
    commandLine("docker", "compose", "down", "--rmi", "local", "-v")
}

tasks.test {
    useJUnitPlatform()
    // test tasks are completed
    mustRunAfter(
        ":lib:crypt4gh:test",
        ":lib:clearinghouse:test",
        ":lib:tsd-file-api-client:test",
        ":services:tsd-api-mock:test",
        ":services:mq-interceptor:test",
        ":services:localega-tsd-proxy:test"
    )
    testLogging.showStandardStreams = true
}
