// Turns Java compile errors into GitHub Actions check annotations.
//
// The raw job log is served from a separate storage host which is not reachable from every network,
// while the checks API always is. Compile errors are therefore re-reported here as workflow commands
// (`::error file=...,line=...::message`), which GitHub turns into annotations:
//
//   gh api /repos/<owner>/<repo>/commits/<sha>/check-runs
//
// The task only runs in CI, never fails the build itself and reports nothing when compilation succeeds
// (its output is then simply not interesting). It is deliberately a separate javac invocation so that a
// failure inside Gradle's own compiler pipeline cannot hide the diagnostics.

if (System.getenv("GITHUB_ACTIONS") == "true") {
    val javaExtension = extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
    val mainSourceSet = javaExtension.sourceSets.getByName("main")
    val compileTask = tasks.named("compileJava", org.gradle.api.tasks.compile.JavaCompile::class.java)

    // Resolved eagerly: configuration cache friendly, and the compile options are final by now.
    val sources = mainSourceSet.allJava.files.toList()
    val classpath = mainSourceSet.compileClasspath.files.toList()
    val release = compileTask.get().options.release.orNull ?: 21

    val preflight = tasks.register("preflightCompile") {
        group = "verification"
        description = "Re-reports compile errors as GitHub check annotations (CI only)."
        val capturedSources = sources
        val capturedClasspath = classpath
        val capturedRelease = release
        doLast {
            runCatching {
                val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
                    ?: error("no system java compiler available")
                val diagnostics = javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>()
                compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8).use { fileManager ->
                    val units = fileManager.getJavaFileObjectsFromFiles(capturedSources.toSet())
                    val options = listOf("--release", capturedRelease.toString(), "-parameters", "-encoding", "UTF-8", "-proc:none")
                    val writer = java.io.StringWriter()
                    compiler.getTask(writer, fileManager, diagnostics, options, null, units).call()
                }
                val errors = diagnostics.diagnostics.filter {
                    it.kind == javax.tools.Diagnostic.Kind.ERROR
                }
                if (errors.isEmpty()) {
                    return@runCatching
                }
                println("::error::${errors.size} compile error(s) in ${project.name}.")
                errors.take(50).forEach { diagnostic ->
                    val file = diagnostic.source?.toUri()?.path?.let { path ->
                        runCatching { rootDir.toPath().relativize(java.nio.file.Path.of(path)).toString() }
                            .getOrDefault(path)
                    } ?: "<unknown>"
                    val line = if (diagnostic.lineNumber > 0) diagnostic.lineNumber else 1
                    val message = (diagnostic.getMessage(null) ?: "compile error")
                        .replace("%", "%25").replace("\n", " ").replace("\r", "")
                    println("::error file=$file,line=$line,title=Compile error::${project.name}: $message")
                }
            }.onFailure { throwable ->
                println("::warning::preflight compile diagnostics failed: ${throwable.message}")
            }
        }
    }

    compileTask.configure { dependsOn(preflight) }
}
