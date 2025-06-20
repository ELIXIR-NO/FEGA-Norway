plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("formatting-conventions")
}

group = "no.elixir.fega"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-console-standalone:1.13.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.13.0")
    testImplementation("com.rabbitmq:amqp-client:5.25.0")
    testImplementation("com.konghq:unirest-java:3.14.5")
    testImplementation("org.postgresql:postgresql:42.7.6")
    testImplementation("com.auth0:java-jwt:4.5.0") // FIXME: io.jsonwebtoken
    testImplementation("commons-io:commons-io:2.19.0")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
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
        ":e2eTests:jar",
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

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // This will exclude duplicate files
    manifest {
        attributes(
            "Main-Class" to "org.junit.platform.console.ConsoleLauncher"
        )
    }
    from(sourceSets["test"].output)
    from(configurations.testRuntimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
