plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("formatting-conventions")
}

java { sourceCompatibility = JavaVersion.VERSION_21 }

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.getByName<Jar>("jar") {
    enabled = false
}

