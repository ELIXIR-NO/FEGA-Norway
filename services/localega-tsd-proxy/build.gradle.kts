plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.lombok)
}

group = "no.elixir.fega"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":lib:clearinghouse"))
    implementation(project(":lib:tsd-file-api-client"))
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation(libs.okhttp)
    implementation(libs.commons.collections4)
    implementation(libs.commons.lang3)
    implementation(libs.gson)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    runtimeOnly(libs.postgresql)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
}

configurations {
    all {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
}
