plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.23.3")
    implementation("org.ow2.asm:asm:8.0.1")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.2.2")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.4")
    implementation("org.hibernate.orm:hibernate-gradle-plugin:6.4.1.Final")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.9.28")
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
