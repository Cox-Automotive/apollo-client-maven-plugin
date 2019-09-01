package com.lahzouz.java.graphql.client.maven.plugin

import org.apache.http.HttpHeaders
import org.apache.http.client.entity.GzipDecompressingEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.nio.charset.Charset


object Introspection {

    fun getIntrospectionSchema(host: String): String {
        val client = HttpClients.custom()
                .disableContentCompression()
                .build()
        val request = HttpPost(host);
        val body = StringEntity(introspectionQuery)

        request.apply {
            entity = body
            setHeader(HttpHeaders.ACCEPT, "application/json")
            setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, identity")
            setHeader(HttpHeaders.ACCEPT_CHARSET, "utf-8")
            setHeader(HttpHeaders.USER_AGENT, "Apollo Client Maven Plugin")
        }
        val response = client.execute(request)
        var schema = ""
        if (200 == response.statusLine.statusCode) {
            var entity = response.entity
            val contentEncodingHeader = entity.contentEncoding
            if (contentEncodingHeader != null) {
                val encodings = contentEncodingHeader.elements
                for (i in encodings.indices) {
                    if (encodings[i].name.equals("gzip", ignoreCase = true)) {
                        entity = GzipDecompressingEntity(entity)
                        break
                    }
                }
            }
            schema = EntityUtils.toString(entity, Charset.forName("utf-8").name())
        }
        client.close()
        return schema
    }

    private const val rawIntrospectionQuery = "\\n" +
            "    query IntrospectionQuery {\\n" +
            "      __schema {\\n" +
            "        queryType { name }\\n" +
            "        mutationType { name }\\n" +
            "        subscriptionType { name }\\n" +
            "        types {\\n" +
            "          ...FullType\\n" +
            "        }\\n" +
            "        directives {\\n" +
            "          name\\n" +
            "          description\\n" +
            "          locations\\n" +
            "          args {\\n" +
            "            ...InputValue\\n" +
            "          }\\n" +
            "        }\\n" +
            "      }\\n" +
            "    }\\n" +
            "\\n" +
            "    fragment FullType on __Type {\\n" +
            "      kind\\n" +
            "      name\\n" +
            "      description\\n" +
            "      fields(includeDeprecated: true) {\\n" +
            "        name\\n" +
            "        description\\n" +
            "        args {\\n" +
            "          ...InputValue\\n" +
            "        }\\n" +
            "        type {\\n" +
            "          ...TypeRef\\n" +
            "        }\\n" +
            "        isDeprecated\\n" +
            "        deprecationReason\\n" +
            "      }\\n" +
            "      inputFields {\\n" +
            "        ...InputValue\\n" +
            "      }\\n" +
            "      interfaces {\\n" +
            "        ...TypeRef\\n" +
            "      }\\n" +
            "      enumValues(includeDeprecated: true) {\\n" +
            "        name\\n" +
            "        description\\n" +
            "        isDeprecated\\n" +
            "        deprecationReason\\n" +
            "      }\\n" +
            "      possibleTypes {\\n" +
            "        ...TypeRef\\n" +
            "      }\\n" +
            "    }\\n" +
            "\\n" +
            "    fragment InputValue on __InputValue {\\n" +
            "      name\\n" +
            "      description\\n" +
            "      type { ...TypeRef }\\n" +
            "      defaultValue\\n" +
            "    }\\n" +
            "\\n" +
            "    fragment TypeRef on __Type {\\n" +
            "      kind\\n" +
            "      name\\n" +
            "      ofType {\\n" +
            "        kind\\n" +
            "        name\\n" +
            "        ofType {\\n" +
            "          kind\\n" +
            "          name\\n" +
            "          ofType {\\n" +
            "            kind\\n" +
            "            name\\n" +
            "            ofType {\\n" +
            "              kind\\n" +
            "              name\\n" +
            "              ofType {\\n" +
            "                kind\\n" +
            "                name\\n" +
            "                ofType {\\n" +
            "                  kind\\n" +
            "                  name\\n" +
            "                  ofType {\\n" +
            "                    kind\\n" +
            "                    name\\n" +
            "                  }\\n" +
            "                }\\n" +
            "              }\\n" +
            "            }\\n" +
            "          }\\n" +
            "        }\\n" +
            "      }\\n" +
            "    }\\n" +
            "  "

    private const val introspectionQuery = "{\"query\": \"$rawIntrospectionQuery\",\"variables\":{},\"operationName\":introspection}"

}