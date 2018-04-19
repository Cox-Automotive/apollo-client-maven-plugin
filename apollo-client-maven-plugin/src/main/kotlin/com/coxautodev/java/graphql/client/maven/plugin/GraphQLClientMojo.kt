package com.coxautodev.java.graphql.client.maven.plugin

import com.apollographql.apollo.compiler.GraphQLCompiler
import com.apollographql.apollo.compiler.NullableValueType
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.FileUtils
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.util.ConfigurationBuilder
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors


/**
 * Generates classes for a graphql API
 */
@Mojo(name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true
)
class GraphQLClientMojo: AbstractMojo() {

    @Parameter(property = "outputDirectory", defaultValue = "\${project.build.directory}/generated-sources/graphql-client")
    private var outputDirectory: File? = null

    @Parameter(property = "basePackage", defaultValue = "com.example.graphql.client")
    private var basePackage: String? = null

    @Parameter(property = "introspectionFile", defaultValue = "\${project.basedir}/src/main/graphql/schema.json")
    private var introspectionFile: File? = null

    @Parameter(property = "addSourceRoot", defaultValue = "true")
    private var addSourceRoot: Boolean? = null

    @Parameter(readonly = true, required = true, defaultValue = "\${project}")
    private var project: MavenProject? = null

    @Parameter(property = "customTypeMap")
    private var customTypeMap: Map<String, String> = mapOf()

    @Throws(MojoExecutionException::class)
    override fun execute() {
        val project = this.project!!
        val outputDirectory = this.outputDirectory!!
        val basePackage = this.basePackage!!
        val introspectionFile = this.introspectionFile!!
        val customTypeMap = this.customTypeMap

        val basePackageDirName = basePackage.replace('.', File.separatorChar)
        val sourceDirName = joinPath("src", "main", "graphql")
        val queryDir = File(project.basedir, sourceDirName)

        if(!queryDir.isDirectory) {
            throw IllegalArgumentException("'${queryDir.absolutePath}' must be a directory")
        }

        val queries = Files.walk(queryDir.toPath())
            .filter { it.toFile().isFile && it.toFile().name.endsWith(".graphql") }
            .map { it.toFile().relativeTo(queryDir) }
            .collect(Collectors.toList())

        if(queries.isEmpty()) {
            throw IllegalArgumentException("No queries found under '${queryDir.absolutePath}")
        }

        val baseTargetDir = File(project.build.directory, joinPath("graphql-schema", sourceDirName, basePackageDirName))
        val schema = File(baseTargetDir, "schema.json")

        val nodeModules = File(project.build.directory, joinPath("apollo-codegen-node-modules", "node_modules"))
        nodeModules.deleteRecursively()
        nodeModules.mkdirs()

        val nodeModuleResources = Reflections(ConfigurationBuilder().setScanners(ResourcesScanner())
            .setUrls(javaClass.getResource("/node_modules")))
            .getResources(Pattern.compile(".*"))

        nodeModuleResources.map { "/$it" }.forEach { resource ->
            val path = resource.replaceFirst("/node_modules/", "").replace(Regex("/"), File.separator)
            val diskPath = File(nodeModules, path)
            diskPath.parentFile.mkdirs()
            FileUtils.copyURLToFile(javaClass.getResource(resource), diskPath)
        }

        val apolloCli = File(nodeModules, joinPath("apollo-codegen", "lib", "cli.js"))
        apolloCli.setExecutable(true)

        if(!introspectionFile.isFile) {
            throw IllegalArgumentException("Introspection JSON not found: ${introspectionFile.absolutePath}")
        }

        if(!apolloCli.isFile) {
            throw IllegalStateException("Apollo codegen cli not found: '${apolloCli.absolutePath}'")
        }

        schema.parentFile.mkdirs()
        queries.forEach { query ->
            val src = File(queryDir, query.path)
            val dest = File(baseTargetDir, query.path)

            dest.parentFile.mkdirs()
            src.copyTo(dest, overwrite = true)
        }

        // https://stackoverflow.com/a/25080297
        // https://stackoverflow.com/questions/32827329/how-to-get-the-full-path-of-an-executable-in-java-if-launched-from-windows-envi
        val node = System.getenv("PATH")?.split(File.pathSeparator)?.map { File(it, "node") }?.find {
            it.isFile && it.canExecute()
        } ?: throw IllegalStateException("No 'node' executable found on PATH!")

        log.info("Found node executable: ${node.absolutePath}")

        val arguments = listOf("generate", *queries.map { File(baseTargetDir, it.path).absolutePath }.toTypedArray(), "--target", "json", "--schema", introspectionFile.absolutePath, "--output", schema.absolutePath)
        log.info("Running apollo cli (${apolloCli.absolutePath}) with arguments: ${arguments.joinToString(" ")}")

        val proc = ProcessBuilder(node.absolutePath, apolloCli.absolutePath, *arguments.toTypedArray())
            .directory(nodeModules.parentFile)
            .inheritIO()
            .start()

        if(proc.waitFor() != 0) {
            throw IllegalStateException("Apollo codegen cli command failed")
        }

        val compiler = GraphQLCompiler()
        compiler.write(GraphQLCompiler.Arguments(schema, outputDirectory, customTypeMap, NullableValueType.JAVA_OPTIONAL, true, true))

        if(addSourceRoot == true) {
            project.addCompileSourceRoot(outputDirectory.absolutePath)
        }
    }

    private fun joinPath(vararg names: String): String = names.joinToString(File.separator)
}

