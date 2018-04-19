package com.coxautodev.java.graphql.client.tests

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.CustomTypeAdapter
import com.coxautodev.graphql.tools.SchemaParser
import com.coxautodev.java.graphql.client.tests.queries.GetBooksQuery
import com.coxautodev.java.graphql.client.tests.queries.author.GetAuthorsQuery
import com.coxautodev.java.graphql.client.tests.type.CustomType
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.execution.SimpleExecutionStrategy
import graphql.servlet.GraphQLServlet
import graphql.servlet.SimpleGraphQLServlet
import io.undertow.Undertow
import io.undertow.servlet.Servlets
import io.undertow.servlet.api.DeploymentInfo
import io.undertow.servlet.api.DeploymentManager
import io.undertow.servlet.util.ImmediateInstanceFactory
import okhttp3.OkHttpClient
import okhttp3.Request
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
        GraphQLServlet servlet = new SimpleGraphQLServlet(
            SchemaParser.newParser()
                .file('schema.graphqls')
                .resolvers(new Query())
                .dataClasses(Query.Book, Query.Author)
                .build()
                .makeExecutableSchema(),

            new SimpleExecutionStrategy()
        )

        DeploymentInfo servletBuilder = Servlets.deployment()
            .setClassLoader(getClass().getClassLoader())
            .setContextPath("/")
            .setDeploymentName("test")
            .addServlets(Servlets.servlet("GraphQLServlet", GraphQLServlet, new ImmediateInstanceFactory<Servlet>(servlet)).addMapping("/graphql/*"))

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder)
        manager.deploy()
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(manager.start()).build()
        server.start()
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort()

        CustomTypeAdapter<Long> longCustomTypeAdapter = new CustomTypeAdapter<Long>() {
            @Override
            Long decode(final String value) {
                return Long.valueOf(value)
            }

            @Override
            String encode(final Long value) {
                return String.valueOf(value)
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
}
