plugins {
    base
}

val goBuild = tasks.register("goBuild", Exec::class) {
    description = "Build the Go e2e binaries (one per cmd/)"
    commandLine("go", "build", "-o", "build/", "./cmd/...")
}

tasks.named("build") {
    dependsOn(goBuild)
}

tasks.register("test", Exec::class) {
    group = "verification"
    description = "Run the Go e2e unit tests"
    commandLine("go", "test", "./...")
}

val goClean = tasks.register("goClean", Exec::class) {
    description = "Deletes the build directory"
    commandLine("go", "clean")
}

tasks.named("clean") {
    dependsOn(goClean)
}
