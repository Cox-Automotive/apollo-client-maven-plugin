# Apollo GraphQL Client Code Generation Maven Plugin

[![CircleCI](https://circleci.com/gh/aoudiamoncef/apollo-client-maven-plugin.svg?style=svg)](https://circleci.com/gh/aoudiamoncef/apollo-client-maven-plugin)
[![Download](https://api.bintray.com/packages/sparow199/maven/apollo-client-maven-plugin/images/download.svg)](https://bintray.com/sparow199/maven/apollo-client-maven-plugin/_latestVersion)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/71b115f870bb44478dac5d05abc9f378)](https://app.codacy.com/app/Sparow199/apollo-client-maven-plugin?utm_source=github.com&utm_medium=referral&utm_content=Sparow199/apollo-client-maven-plugin&utm_campaign=Badge_Grade_Dashboard)
[![Known Vulnerabilities](https://snyk.io/test/github/sparow199/apollo-client-maven-plugin/badge.svg)](https://snyk.io/test/github/Sparow199/apollo-client-maven-plugin)
![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FSparow199%2Fapollo-client-maven-plugin.svg?type=shield)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Usage

A full usage example can be found in the [test project](https://github.com/sparow199/apollo-client-maven-plugin/tree/master/apollo-client-maven-plugin-tests)

### Getting Started

1. Add the apollo runtime library to your project's dependencies:

```xml
<dependencies>
   <dependency>
       <groupId>com.apollographql.apollo</groupId>
       <artifactId>apollo-runtime</artifactId>
       <version>2.1.0</version>
   </dependency>
   <!-- Optional, needed only for ANNOTATED nullable type-->
   <dependency>
       <groupId>org.jetbrains</groupId>
       <artifactId>annotations</artifactId>
       <version>19.0.0</version>
   </dependency>
   <dependency>
       <groupId>org.jetbrains.kotlin</groupId>
       <artifactId>kotlin-reflect</artifactId>
       <version>1.3.72</version>
   </dependency>
</dependencies>
```

2. Add the code generator plugin to your project's build:

```xml
<plugin>
    <groupId>com.github.sparow199</groupId>
    <artifactId>apollo-client-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                    <rootPackageName>com.example.graphql.client</rootPackageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

3. Create a file `src/main/graphql/schema.json` with the JSON results of an [introspection query](https://gist.github.com/aoudiamoncef/a59527016e16a2d56309d62e01ff2348) OR you can
automatically generate this file by setting `generateIntrospectionFile` to true and `schemaUrl` to your GraphQL endpoint. At build time, the plugin will query the server and install this file
per the value of `introspectionFile`.
4. Create files for each query you'd like to generate classes for under `src/main/graphql`:
    1. Query file names must match the name of the query they contain
    2. Query files must end with `.graphql`
    3. Any subdirectories under `src/main/graphql` are treated as extra package names to append to `packageName` in the plugin config.
5. Run `mvn clean generate-sources` to generate classes for your queries.

### Configuration Options

All plugin options and their defaults:

```xml
<configuration>
    <skip>false</skip>
    <addSourceRoot>true</addSourceRoot>
    <introspectionFile>${project.basedir}/src/main/graphql/schema.json</introspectionFile>
    <generateIntrospectionFile>false</generateIntrospectionFile>
    <sourceDirName>${project.basedir}/src/main/graphql</sourceDirName>
    <schemaUrl>http://localhost/graphql</schemaUrl>
    <schemaUrlHeaders></schemaUrlHeaders>
    <useSelfSignedCertificat>false</useSelfSignedCertificat>
    <rootPackageName>com.example.graphql.client</rootPackageName>
    <outputDirectory>${project.build.directory}/generated-sources/graphql-client</outputDirectory>
    <operationIdGeneratorClass>com.apollographql.apollo.compiler.OperationIdGenerator$Sha256</operationIdGeneratorClass>
    <generateModelBuilder>true</generateModelBuilder>
    <useJavaBeansSemanticNaming>true</useJavaBeansSemanticNaming>
    <useSemanticNaming>true</useSemanticNaming>
    <nullableValueType>JAVA_OPTIONAL</nullableValueType>
    <suppressRawTypesWarning>false</suppressRawTypesWarning>
    <generateKotlinModels>false</generateKotlinModels>
    <generateAsInternal>false</generateAsInternal>
    <generateVisitorForPolymorphicDatatypes>false</generateVisitorForPolymorphicDatatypes>
    <customTypeMap></customTypeMap>
    <enumAsSealedClassPatternFilters></enumAsSealedClassPatternFilters>
</configuration>
```

#### Nullable Types

Available nullable types:

```java
ANNOTATED
APOLLO_OPTIONAL
GUAVA_OPTIONAL
JAVA_OPTIONAL
INPUT_TYPE
```     

Properties specified as nullable in the schema will have a java 8 `java.util.optional` type.

#### Custom Types

To use the [Custom Scalar Types](https://github.com/apollographql/apollo-android#custom-scalar-types) you need to 
define mapping configuration then register your custom adapter:  

```xml
<configuration>
    ...
    <customTypeMap>
        <Long>java.lang.Long</Long>
    </customTypeMap>
</configuration>
```

### Using the Client

Assuming a file named `src/main/graphql/GetBooks.graphql` is defined that contains a query named `GetBooks` against the given `schema.json`, the following code demonstrates how that query could be executed.

```java
ApolloClient client = ApolloClient.builder()
    .serverUrl("https://example.com/graphql")
    .okHttpClient(new OkHttpClient.Builder()
        .addInterceptor(new Interceptor() {
            @Override
            Response intercept(Interceptor.Chain chain) throws IOException {
                chain.proceed(chain.request().newBuilder().addHeader("Authorization", "Basic cnllYnJ5ZTpidWJibGVzMTIz").build())
            }
        })
        .build())
    .build()

client.newCall(new GetBooks())
    .enqueue(new ApolloCall.Callback<GetBooks.Data>() {

    @Override public void onResponse(@NotNull Response<GetBooks.Data> response) {
        ...
    }

    @Override public void onFailure(@NotNull ApolloException t) {
        ...
    }
    });
```

#### Wrap ApolloCall with a CompletableFuture

If you miss **apolloCall.execute** method, which execute a query synchronously, you could wrap **apolloCall.enqueue**
with a CompletableFuture and call **join** method to wait for the response

```java
public class ApolloClientUtils {

    public static <T> CompletableFuture<Response<T>> toCompletableFuture(ApolloCall<T> apolloCall) {
        CompletableFuture<Response<T>> completableFuture = new CompletableFuture<>();

        completableFuture.whenComplete((tResponse, throwable) -> {
            if (completableFuture.isCancelled()) {
                completableFuture.cancel(true);
            }
        });

        apolloCall.enqueue(new ApolloCall.Callback<T>() {
            @Override
            public void onResponse(@NotNull Response<T> response) {
                completableFuture.complete(response);
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                completableFuture.completeExceptionally(e);
            }
        });

        return completableFuture;
    }
}
```
#### Using Apollo without `apollo-runtime`

See [documentation](https://www.apollographql.com/docs/android/advanced/no-runtime/)