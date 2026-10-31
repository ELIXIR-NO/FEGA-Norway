plugins {
    base
}

val legaTests = tasks.register("test", Exec::class) {
    group = "verification"
    description = "Test the LEGA Commander application"
    commandLine("go", "test", "./...")
}

val goBuild = tasks.register("go-build", Exec::class) {
    dependsOn(legaTests)
    description = "Build the LEGA Commander application"
    commandLine("go", "build", "-o", "build/")
    finalizedBy(testLegaCommander)
}

val testLegaCommander = tasks.register<Exec>("test-lega-commander") {
    group = "verification"
    description = "Test lega-commander -h"
    executable = "./build/lega-commander"
    args("-h")
}

val goClean = tasks.register("go-clean", Exec::class) {
    description = "Deletes the build directory"
    commandLine("go", "clean")
}

tasks.named("build") {
    dependsOn(goBuild)
}

tasks.named("clean") {
    dependsOn(goClean)
}

