plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.4.4")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.hibernate.orm:hibernate-gradle-plugin:6.6.11.Final")
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
