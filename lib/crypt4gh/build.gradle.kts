plugins {
    id("java")
    id("extra-java-module-info")
    id("io.freefair.lombok") version "8.13.1"
    id("formatting-conventions")
    id("maven-publish")
}

group = "no.elixir"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("com.rfksystems:blake2b:2.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Note: the project version should be set with the argument "-Pversion=<version>" when building
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Elixir Norway",
            "Main-Class" to "no.elixir.crypt4gh.app.Main"
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Crypt4GH")
                description.set("Crypt4GH standard implementation")
                url.set("https://github.com/ELIXIR-NO/FEGA-Norway/tree/main/lib/crypt4gh")
                organization {
                    name.set("Elixir Norway")
                    url.set("https://elixir.no")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/MIT")
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/ELIXIR-NO/FEGA-Norway.git")
                    developerConnection.set("scm:git:ssh://github.com:ELIXIR-NO/FEGA-Norway.git")
                    url.set("https://github.com/ELIXIR-NO/FEGA-Norway")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/ELIXIR-NO/FEGA-Norway/issues")
                }
                // Add <developers> block with roles to pom.xml
                withXml {
                    val root = asNode()

                    // Remove any existing <developers> section just in case
                    root.children().removeIf { (it as? groovy.util.Node)?.name() == "developers" }

                    // Create <developers>
                    val developers = root.appendNode("developers")

                    /**
                     * @param id
                     * @param name
                     * @param roles
                     */
                    fun addDeveloper(id: String, name: String, roles: List<String>) {
                        val dev = developers.appendNode("developer")
                        dev.appendNode("id", id)
                        dev.appendNode("name", name)
                        val rolesNode = dev.appendNode("roles")
                        roles.forEach { rolesNode.appendNode("role", it) }
                    }

                    addDeveloper("dtitov", "Dmytro Titov", listOf("Lead Developer (emeritus)"))
                    addDeveloper("kjetilkl", "Kjetil Klepper", listOf("Developer", "Maintainer"))
                    addDeveloper("a-ghanem", "Ahmed Ghanem", listOf("Maintainer"))
                    addDeveloper("lyytinen", "Jussi Lyytinen", listOf("Contributor"))
                    addDeveloper("brainstorm", "Roman Valls Guimera", listOf("Contributor"))
                }
            }
        }
    }
    repositories {
        maven {
            name = "fega-norway-crypt4gh"
            url = uri("https://maven.pkg.github.com/ELIXIR-NO/FEGA-Norway")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
