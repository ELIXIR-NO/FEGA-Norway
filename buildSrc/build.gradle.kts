plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.ow2.asm:asm:8.0.1")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.2.4")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.4")
    implementation("org.hibernate.orm:hibernate-gradle-plugin:6.4.4.Final")
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
