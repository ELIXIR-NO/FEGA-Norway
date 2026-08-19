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
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-console-standalone:6.1.1")
    testImplementation("com.rabbitmq:amqp-client:5.32.0")
    testImplementation("com.konghq:unirest-java:3.14.5")
    testImplementation("org.postgresql:postgresql:42.7.11")
    testImplementation("io.jsonwebtoken:jjwt-api:0.13.0")
    testRuntimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    testRuntimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    testImplementation("commons-io:commons-io:2.22.0")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.18")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
}

// The stack itself lives in ../e2e and is driven by ../dev.sh; this module only
// builds the runner. Bring the stack up with `E2E_SUITE=java ./dev.sh start`.

tasks.test {
    useJUnitPlatform()
    // test tasks are completed
    mustRunAfter(
        ":lib:crypt4gh:test",
        ":lib:clearinghouse:test",
        ":lib:tsd-file-api-client:test",
        ":services:tsd-api-mock:test",
        ":services:mq-interceptor:test",
        ":services:localega-tsd-proxy:test",
    )
    testLogging.showStandardStreams = true
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // This will exclude duplicate files
    manifest {
        attributes(
            "Main-Class" to "org.junit.platform.console.ConsoleLauncher",
        )
    }
    from(sourceSets["test"].output)
    from(configurations.testRuntimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
