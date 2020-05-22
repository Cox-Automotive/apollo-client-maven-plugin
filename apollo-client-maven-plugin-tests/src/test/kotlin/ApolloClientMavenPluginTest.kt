package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.CustomTypeAdapter
import com.apollographql.apollo.api.CustomTypeValue
import com.coxautodev.graphql.tools.SchemaParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.lahzouz.java.graphql.client.tests.queries.GetBooksQuery
import com.lahzouz.java.graphql.client.tests.queries.author.GetAuthorsQuery
import com.lahzouz.java.graphql.client.tests.type.CustomType
import graphql.schema.GraphQLSchema
import graphql.servlet.DefaultGraphQLSchemaProvider
import graphql.servlet.GraphQLInvocationInputFactory
import graphql.servlet.SimpleGraphQLHttpServlet
import io.undertow.Undertow
import io.undertow.servlet.Servlets
import io.undertow.servlet.util.ImmediateInstanceFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File
import java.math.BigDecimal
import java.net.InetSocketAddress
import javax.servlet.Servlet


/**
 * @author AOUDIA Moncef
 */
@TestInstance(PER_CLASS)
class ApolloClientMavenPluginTest {

    private lateinit var server: Undertow
    private var port: Int = 0
    private lateinit var client: ApolloClient

    @BeforeAll
    fun setupSpec() {

        val libSchema = SchemaParser.newParser()
                .file("schema.graphqls")
                .resolvers(Query())
                .build()
                .makeExecutableSchema()

        val servlet = createServlet(libSchema)

        val servletBuilder = Servlets.deployment()
                .setClassLoader(javaClass.classLoader)
                .setContextPath("/")
                .setDeploymentName("test")
                .addServlets(Servlets.servlet("GraphQLServlet", SimpleGraphQLHttpServlet::class.java,
                        ImmediateInstanceFactory(servlet as Servlet)).addMapping("/graphql/*"))

        val manager = Servlets.defaultContainer().addDeployment(servletBuilder)
        manager.deploy()
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(manager.start()).build()
        server.start()

        val inetSocketAddress: InetSocketAddress = server.listenerInfo[0].address as InetSocketAddress
        port = inetSocketAddress.port

        val longCustomTypeAdapter = object : CustomTypeAdapter<Long> {
            override fun encode(value: Long): CustomTypeValue<*> {
                return value.toString() as CustomTypeValue<String>
            }

            override fun decode(value: CustomTypeValue<*>): Long {
                return (value.value as BigDecimal).toLong()
            }
        }

        client = ApolloClient.builder()
                .serverUrl("http://127.0.0.1:$port/graphql")
                .addCustomTypeAdapter(CustomType.LONG, longCustomTypeAdapter)
                .okHttpClient(OkHttpClient())
                .build()
    }

    @AfterAll
    fun cleanupSpec() {
        server.stop()
    }

    @Test
    @DisplayName("print introspection query results")
    fun introspectionQueryTest() {
        val mapper = ObjectMapper()
        val data = mapper.readValue(
                OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:$port/graphql/schema.json").build())
                        .execute()
                        .body()?.byteStream(),
                Map::class.java
        )
        assertThat(data).isNotEmpty

        File("src/main/graphql/schema.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data))
    }

    @Test
    @DisplayName("generated book query returns data")
    fun bookQueryTest() {
        val response = client.query(GetBooksQuery()).toCompletableFuture().join()
        assertThat(response.data?.get()?.books).isNotEmpty.hasSize(4)
    }

    @Test
    @DisplayName("generated author query returns data")
    fun authorQueryTest() {
        val response = client.query(GetAuthorsQuery()).toCompletableFuture().join()
        assertThat(response.data?.get()?.authors).isNotEmpty.hasSize(2)
    }

    private fun createServlet(schema: GraphQLSchema): SimpleGraphQLHttpServlet {
        val schemaProvider = DefaultGraphQLSchemaProvider(schema)
        val invocationInputFactory = GraphQLInvocationInputFactory.newBuilder(schemaProvider).build()
        return SimpleGraphQLHttpServlet.newBuilder(invocationInputFactory).build()
    }
}
