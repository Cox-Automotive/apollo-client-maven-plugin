package com.lahzouz.java.graphql.client.maven.plugin

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
class GraphQLClientMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}")
    private lateinit var project: MavenProject

    @Parameter(property = "outputDirectory", defaultValue = "\${project.build.directory}/generated-sources/graphql-client")
    lateinit var outputDirectory: File

    @Parameter(readonly = true, property = "introspectionFile", defaultValue = "\${project.basedir}/src/main/graphql/schema.json")
    lateinit var introspectionFile: File

    @Parameter(property = "basePackage", defaultValue = "com.example.graphql.client")
    private lateinit var basePackage: String

    @Parameter(property = "outputPackage", defaultValue = "com.example.graphql.client")
    private lateinit var outputPackage: String

    @Parameter(property = "customTypeMap")
    private var customTypeMap: Map<String, String> = emptyMap()

    @Parameter(property = "nullableValueType", defaultValue = "JAVA_OPTIONAL")
    private var nullableValueType: NullableValueType = NullableValueType.JAVA_OPTIONAL

    @Parameter(property = "generateIntrospectionFile", defaultValue = "false")
    private var generateIntrospectionFile: Boolean = false

    @Parameter(property = "schemaUrl", defaultValue = "http://localhost")
    private lateinit var schemaUrl: String

    @Parameter(property = "skip", defaultValue = "false")
    private var skip: Boolean = false

    @Parameter(property = "addSourceRoot", defaultValue = "true")
    private var addSourceRoot: Boolean = true

    @Parameter(property = "useSemanticNaming", defaultValue = "true")
    private var useSemanticNaming: Boolean = true

    @Parameter(property = "generateModelBuilder", defaultValue = "true")
    private var generateModelBuilder: Boolean = true

    @Parameter(property = "suppressRawTypesWarning", defaultValue = "false")
    private var suppressRawTypesWarning: Boolean = false

    @Parameter(property = "useJavaBeansSemanticNaming", defaultValue = "true")
    private var useJavaBeansSemanticNaming: Boolean = true

    @Parameter(property = "generateKotlinModels", defaultValue = "false")
    private var generateKotlinModels: Boolean = true


    @Throws(MojoExecutionException::class)
    override fun execute() {

        if (skip) {
            log.info("Skipping execution because skip option is true")
            return
        }

        val nodeModules = File(project.build.directory, joinPath("apollo-codegen-node-modules", "node_modules"))
        nodeModules.deleteRecursively()
        nodeModules.mkdirs()

        val nodeModuleResources = Reflections(ConfigurationBuilder().setScanners(ResourcesScanner())
                .setUrls(javaClass.getResource("/node_modules")))
                .getResources(Pattern.compile(".*"))

        nodeModuleResources.map { "/$it" }.forEach { resource ->
            val path = resource.replaceFirst("/node_modules/", "").replace("/", File.separator)
            val diskPath = File(nodeModules, path)
            diskPath.parentFile.mkdirs()
            FileUtils.copyURLToFile(javaClass.getResource(resource), diskPath)
        }

        val apolloCli = File(nodeModules, joinPath("apollo-codegen", "lib", "cli.js"))
        apolloCli.setExecutable(true)

        if (!apolloCli.isFile) {
            throw MojoExecutionException("Apollo codegen cli not found: '${apolloCli.absolutePath}'")
        }

        if (generateIntrospectionFile) {
            log.info("Automatically generating introspection file")
            val arguments = listOf("introspect-schema", schemaUrl, "--output", introspectionFile.absolutePath)
            log.info("Running apollo cli (${apolloCli.absolutePath}) with arguments: ${arguments.joinToString(" ")}")

            val proc = ProcessBuilder("node", apolloCli.absolutePath, *arguments.toTypedArray())
                    .directory(nodeModules.parentFile)
                    .inheritIO()
                    .start()

            if (proc.waitFor() != 0) {
                throw MojoExecutionException("Apollo codegen cli command failed")
            }

        }

        log.info("Apollo GraphQL Client Code Generation task started")
        val basePackageDirName = basePackage.replace('.', File.separatorChar)
        val sourceDirName = joinPath("src", "main", "graphql")
        val queryDir = File(project.basedir, sourceDirName)
        if (!queryDir.isDirectory) {
            throw MojoExecutionException("'${queryDir.absolutePath}' must be a directory")
        }

        log.info("Read queries started")
        val queries = Files.walk(queryDir.toPath())
                .filter { it.toFile().isFile && it.toFile().name.endsWith(".graphql") }
                .map { it.toFile().relativeTo(queryDir) }
                .collect(Collectors.toList())
        if (queries.isEmpty()) {
            throw MojoExecutionException("No queries found under '${queryDir.absolutePath}")
        }

        val baseTargetDir = File(project.build.directory, joinPath("graphql-schema", sourceDirName, basePackageDirName))
        val schema = File(baseTargetDir, "schema.json")



        if (!introspectionFile.isFile) {
            throw MojoExecutionException("Introspection JSON not found: ${introspectionFile.absolutePath}")
        }

        schema.parentFile.mkdirs()
        queries.forEach { query ->
            val src = File(queryDir, query.path)
            val dest = File(baseTargetDir, query.path)

            dest.parentFile.mkdirs()
            src.copyTo(dest, overwrite = true)
        }

        val arguments = listOf("generate", *queries.map { File(baseTargetDir, it.path).absolutePath }.toTypedArray(), "--target", "json", "--schema", introspectionFile.absolutePath, "--output", schema.absolutePath)
        log.info("Running apollo cli (${apolloCli.absolutePath}) with arguments: ${arguments.joinToString(" ")}")

        val proc = ProcessBuilder("node", apolloCli.absolutePath, *arguments.toTypedArray())
                .directory(nodeModules.parentFile)
                .inheritIO()
                .start()

        if (proc.waitFor() != 0) {
            throw MojoExecutionException("Apollo codegen cli command failed")
        }

        val compiler = GraphQLCompiler()
        compiler.write(GraphQLCompiler.Arguments(
                irFile = schema,
                outputDir = outputDirectory,
                customTypeMap = customTypeMap,
                nullableValueType = nullableValueType,
                useSemanticNaming = useSemanticNaming,
                generateModelBuilder = generateModelBuilder,
                generateKotlinModels = generateKotlinModels,
                suppressRawTypesWarning = suppressRawTypesWarning,
                useJavaBeansSemanticNaming = useJavaBeansSemanticNaming,
                outputPackageName = outputPackage))

        if (addSourceRoot) {
            project.addCompileSourceRoot(outputDirectory.absolutePath)
        }
        log.info("Apollo GraphQL Client Code Generation task finished")
    }

    private fun joinPath(vararg names: String): String = names.joinToString(File.separator)
}



