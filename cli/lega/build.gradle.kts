plugins {
    base
}

val legaTests = tasks.register("test", Exec::class) {
    group = "verification"
    description = "Test the lega application"
    commandLine("go", "test", "./...")
}

val goBuild = tasks.register("go-build", Exec::class) {
    dependsOn(legaTests)
    description = "Build the lega application"
    commandLine("go", "build", "-o", "build/lega", ".")
    finalizedBy(testLega)
}

val testLega = tasks.register<Exec>("test-lega") {
    group = "verification"
    description = "Test lega --help"
    executable = "./build/lega"
    args("--help")
}

val goClean = tasks.register("go-clean", Exec::class) {
    description = "Removes the go build cache entries for this module"
    commandLine("go", "clean")
}

tasks.named("build") {
    dependsOn(goBuild)
}

tasks.named("clean") {
    dependsOn(goClean)
}
