package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.response.CustomTypeAdapter
import com.apollographql.apollo.response.CustomTypeValue
import com.coxautodev.graphql.tools.SchemaParser
import com.example.graphql.client.type.CustomType
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.GraphQLSchema
import graphql.servlet.DefaultGraphQLSchemaProvider
import graphql.servlet.GraphQLInvocationInputFactory
import graphql.servlet.GraphQLSchemaProvider
import graphql.servlet.SimpleGraphQLHttpServlet
import io.undertow.Undertow
import io.undertow.servlet.Servlets
import io.undertow.servlet.api.DeploymentInfo
import io.undertow.servlet.api.DeploymentManager
import io.undertow.servlet.util.ImmediateInstanceFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.annotations.NotNull
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.servlet.Servlet

/**
 * @author Andrew Potter
 */
class IntegrationSpec extends Specification {

    @Shared
    Undertow server

    @Shared
    int port

    @Shared
    ApolloClient client

    def setupSpec() {

        GraphQLSchema libSchema = SchemaParser.newParser()
                .file('schema.graphqls')
                .resolvers(new Query())
                .build()
                .makeExecutableSchema()

        SimpleGraphQLHttpServlet servlet = createServlet(libSchema)

        DeploymentInfo servletBuilder = Servlets.deployment()
            .setClassLoader(getClass().getClassLoader())
            .setContextPath("/")
            .setDeploymentName("test")
            .addServlets(Servlets.servlet("GraphQLServlet", SimpleGraphQLHttpServlet, new ImmediateInstanceFactory<Servlet>(servlet)).addMapping("/graphql/*"))

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder)
        manager.deploy()
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(manager.start()).build()
        server.start()
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort()

        CustomTypeAdapter<Long> longCustomTypeAdapter = new CustomTypeAdapter<Long>() {

            @Override
            Long decode(@NotNull CustomTypeValue value) {
                return Long.valueOf(value as String)
            }

            @Override
            CustomTypeValue encode(@NotNull Long value) {
                return String.valueOf(value) as CustomTypeValue
            }
        }

        client = ApolloClient.builder()
            .serverUrl("http://127.0.0.1:$port/graphql")
            .addCustomTypeAdapter(CustomType.LONG, longCustomTypeAdapter)
            .okHttpClient(new OkHttpClient())
            .build()
    }

    def cleanupSpec() {
        server.stop()
    }

    @Ignore
    // Comment @Ignore and run this to update the schema file (then un-comment @Ignore).
    def "print introspection query results"() {
        expect:
            ObjectMapper mapper = new ObjectMapper()

            Map data = mapper.readValue(
                new OkHttpClient().newCall(new Request.Builder().url("http://127.0.0.1:$port/graphql/schema.json").build())
                    .execute()
                    .body()
                    .byteStream(),
                Map
            )

            new File("src/main/graphql/schema.json").withWriter { out ->
                out << mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data.data)
            }
    }

    def "generated book query returns data"() {
        when:
            def books = client.newCall(new GetBooksQuery()).execute().data().get().books()
        then:
            books.size() == 4
            books.each { b -> assert b.id() instanceof Long}
    }

    def "generated author query returns data"() {
        expect:
            client.newCall(new GetAuthorsQuery()).execute().data().get().authors().size() == 2
    }

    SimpleGraphQLHttpServlet createServlet(GraphQLSchema schema) {
        GraphQLSchemaProvider schemaProvider = new DefaultGraphQLSchemaProvider(schema)
        GraphQLInvocationInputFactory invocationInputFactory = GraphQLInvocationInputFactory.newBuilder(schemaProvider).build()
        return SimpleGraphQLHttpServlet.newBuilder(invocationInputFactory).build()
    }

}
