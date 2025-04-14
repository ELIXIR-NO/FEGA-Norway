plugins {
    id("java")
    id("springboot-conventions")
    id("jsonwebtoken")
}

group = "no.elixir.fega"

dependencies {
    implementation(project(":lib:clearinghouse"))
    implementation(project(":lib:tsd-file-api-client"))
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    runtimeOnly("org.postgresql:postgresql")
}

configurations {
    all {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
}
