package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.response.CustomTypeAdapter
import com.apollographql.apollo.response.CustomTypeValue
import com.coxautodev.graphql.tools.SchemaParser
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.servlet.Servlet


/**
 * @author Sparow199
 */
@TestInstance(PER_CLASS)
class IntegrationSpec {

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
                .addServlets(Servlets.servlet("GraphQLServlet", SimpleGraphQLHttpServlet::class.java, ImmediateInstanceFactory<Servlet>(servlet)).addMapping("/graphql/*"))

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

    //
    @AfterAll
    fun cleanupSpec() {
        server.stop()
    }


    @Test
    fun `print introspection query results`() {
        val mapper = ObjectMapper()
        val data = mapper.readValue(
                OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:$port/graphql/schema.json").build())
                        .execute()
                        .body()?.byteStream(),
                Map::class.java
        )

        File("src/main/graphql/schema.json").writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data["data"]))
    }

    @Test
    fun `generated book query returns data`() {
        val future: CompletableFuture<Response<Optional<GetBooksQuery.Data>>> = CompletableFuture()
        client.query(GetBooksQuery()).enqueue(TestCallbackFuture(future))
        val response = future.join()
        Assertions.assertEquals(4, response.data()?.get()?.books?.size)
        Assertions.assertTrue(response.data()?.get()?.books?.get(0)?.getId() is Long)
        Assertions.assertTrue(response.data()?.get()?.books?.get(3)?.getId() is Long)
    }

    @Test
    fun `generated author query returns data`() {
        val future: CompletableFuture<Response<Optional<GetAuthorsQuery.Data>>> = CompletableFuture()
        client.query(GetAuthorsQuery()).enqueue(TestCallbackFuture(future))
        val response = future.join()
        Assertions.assertEquals(2, response.data()?.get()?.authors?.size)
    }

    private fun createServlet(schema: GraphQLSchema): SimpleGraphQLHttpServlet {
        val schemaProvider = DefaultGraphQLSchemaProvider(schema)
        val invocationInputFactory = GraphQLInvocationInputFactory.newBuilder(schemaProvider).build()
        return SimpleGraphQLHttpServlet.newBuilder(invocationInputFactory).build()
    }

}
