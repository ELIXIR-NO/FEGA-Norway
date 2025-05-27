plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.3")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.0")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}

gradlePlugin {
    plugins {
        // here we register our plugin with an ID
        register("extra-java-module-info") {
            id = "extra-java-module-info"
            implementationClass = "org.gradle.transform.javamodules.ExtraModuleInfoPlugin"
        }
    }
}
