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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-console-standalone:1.13.4")
    testImplementation("com.rabbitmq:amqp-client:5.26.0")
    testImplementation("com.konghq:unirest-java:3.14.5")
    testImplementation("org.postgresql:postgresql:42.7.8")
    testImplementation("com.auth0:java-jwt:4.5.0")
    testImplementation("commons-io:commons-io:2.20.0")
    testImplementation(project(":lib:crypt4gh"))
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
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
