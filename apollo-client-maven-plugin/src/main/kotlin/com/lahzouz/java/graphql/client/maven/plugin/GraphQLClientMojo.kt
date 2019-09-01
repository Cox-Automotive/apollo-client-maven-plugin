package com.lahzouz.java.graphql.client.maven.plugin

import com.apollographql.apollo.compiler.GraphQLCompiler
import com.apollographql.apollo.compiler.NullableValueType
import com.apollographql.apollo.compiler.parser.GraphQLDocumentParser
import com.apollographql.apollo.compiler.parser.Schema
import com.lahzouz.java.graphql.client.maven.plugin.Introspection.getIntrospectionSchema
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors


/**
 * Generate queries classes for a GraphQl API
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

    @Parameter(property = "irPackageName", defaultValue = "com.example.graphql.client")
    private lateinit var irPackageName: String

    @Parameter(property = "outputPackage", defaultValue = "com.example.graphql.client")
    private lateinit var outputPackage: String

    @Parameter(property = "schemaUrl", defaultValue = "http://localhost")
    private lateinit var schemaUrl: String

    @Parameter(property = "sourceDirName", defaultValue = "\${project.basedir}/src/main/graphql")
    private lateinit var sourceDirName: String

    @Parameter(property = "customTypeMap")
    private var customTypeMap: Map<String, String> = emptyMap()

    @Parameter(property = "nullableValueType", defaultValue = "JAVA_OPTIONAL")
    private var nullableValueType: NullableValueType = NullableValueType.JAVA_OPTIONAL

    @Parameter(property = "generateIntrospectionFile", defaultValue = "false")
    private var generateIntrospectionFile: Boolean = false

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
    private var generateKotlinModels: Boolean = false

    @Parameter(property = "generateVisitorForPolymorphicDatatypes", defaultValue = "false")
    private var generateVisitorForPolymorphicDatatypes: Boolean = true

    @Throws(MojoExecutionException::class)
    override fun execute() {

        if (skip) {
            log.info("Skipping execution because skip option is true")
            return
        }

        log.info("Apollo GraphQL Client Code Generation task started")
        val queryDir = File(sourceDirName)

        if (!queryDir.isDirectory) {
            throw MojoExecutionException("'${queryDir.absolutePath}' must be a directory")
        }

        log.info("Read queries files")
        val queries = Files.walk(queryDir.toPath())
                .filter { it.toFile().isFile && it.toFile().name.endsWith(".graphql") }
                .map { it.toFile() }
                .collect(Collectors.toList())

        if (queries.isEmpty()) {
            throw MojoExecutionException("No queries found under '${queryDir.absolutePath}")
        }

        if (generateIntrospectionFile) {
            log.info("Automatically generating introspection file from $schemaUrl")
            val schema = getIntrospectionSchema(schemaUrl)
            if (schema.isNotEmpty()) {
                File(introspectionFile.toURI()).writeText(schema)
            } else {
                throw MojoExecutionException("Error, can't generate introspection schema file from: $schemaUrl")
            }
        }

        if (!introspectionFile.isFile) {
            throw MojoExecutionException("Introspection schema file not found: ${introspectionFile.absolutePath}")
        }

        val graphQLDocumentParser = GraphQLDocumentParser(Schema(introspectionFile))
        val ir = graphQLDocumentParser.parse(queries)

        val compiler = GraphQLCompiler()
        compiler.write(GraphQLCompiler.Arguments(
                irFile = introspectionFile,
                ir = ir,
                outputDir = outputDirectory,
                customTypeMap = customTypeMap,
                nullableValueType = nullableValueType,
                useSemanticNaming = useSemanticNaming,
                generateModelBuilder = generateModelBuilder,
                useJavaBeansSemanticNaming = useJavaBeansSemanticNaming,
                irPackageName = irPackageName,
                outputPackageName = outputPackage,
                suppressRawTypesWarning = suppressRawTypesWarning,
                generateKotlinModels = generateKotlinModels,
                generateVisitorForPolymorphicDatatypes = generateVisitorForPolymorphicDatatypes)
        )

        if (addSourceRoot) {
            log.info("Add the compiled sources to project root")
            project.addCompileSourceRoot(outputDirectory.absolutePath)
        }
        log.info("Apollo GraphQL Client Code Generation task finished")
    }


}
