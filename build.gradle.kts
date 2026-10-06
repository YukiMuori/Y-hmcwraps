println("::notice:::: root script start")

allprojects {
    group = "de.skyslycer"
    version = "2.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.skyslycer.de/jitpack")
        maven("https://repo.skyslycer.de/mirrors")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.dmulloy2.net/repository/public/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.bytecode.space/repository/maven-public/")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://repo.triumphteam.dev/snapshots")
        maven("https://repo.nexomc.com/releases/")
        maven("https://repo.momirealms.net/releases/")
        // Temp repository until Kyori updates their stuff (nothing is more permanent than a temporary solution)
        maven("https://repo.granny.dev/snapshots/")
        maven("https://repo.artillex-studios.com/releases/") // AxAuctions
        maven("https://repo.tcoded.com/releases/") // FoliaLib
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/") // MMOItems
    }
}

// ---------------------------------------------------------------------------
// CI diagnostics
//
// The GitHub Actions job log is served from a storage host that is not reachable from every network,
// so a failing build is hard to diagnose. This applies a small script to every subproject that
// re-reports compiler diagnostics as check annotations, which are always readable:
//
//   gh api /repos/<owner>/<repo>/commits/<sha>/check-runs
//
// Everything is wrapped in try/catch: a problem in the diagnostics must never break a build.
// ---------------------------------------------------------------------------
subprojects {
    val subproject = this
    pluginManager.withPlugin("java") {
        println("::notice::java plugin applied in " + subproject.name)
        val diagnosticsScript = rootProject.file("gradle/ci-annotations.gradle")
        if (System.getenv("GITHUB_ACTIONS") == "true" && diagnosticsScript.exists()) {
            try {
                subproject.apply { from(diagnosticsScript) }
            } catch (throwable: Throwable) {
                println("::error::Could not apply the CI diagnostics script to " + subproject.name + ": " + throwable.message)
            }
        }
    }
}

gradle.projectsEvaluated {
    println("::notice::all projects configured: " + allprojects.joinToString(", ") { it.name })
}

gradle.taskGraph.whenReady {
    println("::notice::task graph ready with " + allTasks.size + " tasks")
}

tasks.register("build") {
    group = "build"
    description = "Aggregate task to build all modules"
}

tasks.register<Copy>("copyPluginJar") {
    dependsOn(":core:build")
    val coreProject = project(":core")
    val version = coreProject.version.toString()
    val jarName = "core-$version-all.jar"
    val coreJar = coreProject.layout.buildDirectory.file("libs/$jarName")
    from(coreJar)
    into(layout.buildDirectory.dir("libs"))
    rename { "HMCWraps-$version.jar" }
}

tasks.named("build") {
    dependsOn(":api:build", ":core:build", "copyPluginJar")
}