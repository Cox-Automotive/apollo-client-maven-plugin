package com.lahzouz.java.graphql.client.maven.plugin

import com.apollographql.apollo.compiler.GraphQLCompiler
import com.apollographql.apollo.compiler.NullableValueType
import com.apollographql.apollo.compiler.PackageNameProvider
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

    @Parameter(property = "introspectionFile", defaultValue = "\${project.basedir}/src/main/graphql/schema.json")
    private lateinit var introspectionFile: File

    @Parameter(property = "transformedQueriesOutputDir", defaultValue = "\${project.build.directory}/generated-sources/graphql-client/transformed")
    private var transformedQueriesOutputDir: File? = null

    @Parameter(property = "outputDirectory", defaultValue = "\${project.build.directory}/generated-sources/graphql-client")
    private lateinit var outputDirectory: File

    @Parameter(property = "rootPackageName", defaultValue = "com.example.graphql.client")
    private lateinit var rootPackageName: String

    @Parameter(property = "schemaPackageName", defaultValue = "schema")
    private lateinit var schemaPackageName: String

    @Parameter(property = "schemaUrl", defaultValue = "http://localhost/graphql")
    private lateinit var schemaUrl: String

    @Parameter(property = "sourceDirName", defaultValue = "\${project.basedir}/src/main/graphql")
    private lateinit var sourceDirName: String

    @Parameter(property = "customTypeMap")
    private var customTypeMap: Map<String, String> = emptyMap()

    @Parameter(property = "nullableValueType", defaultValue = "JAVA_OPTIONAL")
    private lateinit var nullableValueType: NullableValueType

    @Parameter(property = "generateTransformedQueries")
    private var generateTransformedQueries: Boolean = false

    @Parameter(property = "generateIntrospectionFile")
    private var generateIntrospectionFile: Boolean = false

    @Parameter(property = "skip")
    private var skip: Boolean = false

    @Parameter(property = "addSourceRoot")
    private var addSourceRoot: Boolean = true

    @Parameter(property = "useSemanticNaming")
    private var useSemanticNaming: Boolean = true

    @Parameter(property = "generateModelBuilder")
    private var generateModelBuilder: Boolean = true

    @Parameter(property = "suppressRawTypesWarning")
    private var suppressRawTypesWarning: Boolean = false

    @Parameter(property = "useJavaBeansSemanticNaming")
    private var useJavaBeansSemanticNaming: Boolean = true

    @Parameter(property = "generateKotlinModels")
    private var generateKotlinModels: Boolean = false

    @Parameter(property = "generateVisitorForPolymorphicDatatypes")
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

        if (!generateTransformedQueries) {
            transformedQueriesOutputDir = null
        }

        val packageNameProvider = PackageNameProvider(rootPackageName, schemaPackageName, null)
        val graphQLDocumentParser = GraphQLDocumentParser(Schema(introspectionFile), packageNameProvider)
        val ir = graphQLDocumentParser.parse(queries)

        val compiler = GraphQLCompiler()
        compiler.write(GraphQLCompiler.Arguments(
                ir = ir,
                outputDir = outputDirectory,
                customTypeMap = customTypeMap,
                nullableValueType = nullableValueType,
                useSemanticNaming = useSemanticNaming,
                generateModelBuilder = generateModelBuilder,
                useJavaBeansSemanticNaming = useJavaBeansSemanticNaming,
                packageNameProvider = packageNameProvider,
                transformedQueriesOutputDir = transformedQueriesOutputDir,
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
