# Apollo GraphQL Client Code Generation Maven Plugin

## Usage

A full usage example can be found in the [test project](https://github.com/Cox-Automotive/apollo-client-maven-plugin/tree/master/apollo-client-maven-plugin-tests)

### Getting Started

**NOTE: This plugin requires a nodejs environment to execute the bundled apollo-codegen node module.**

1. Add the apollo runtime library and guava to your project's depedencies:
    ```xml
    <dependency>
        <groupId>com.apollographql.apollo</groupId>
        <artifactId>apollo-runtime</artifactId>
        <version>0.4.0</version>
    </dependency>
    ```
2. Add the code generator plugin to your project's build (if codegen is desired):
    ```xml
    <plugin>
        <groupId>com.coxautodev</groupId>
        <artifactId>apollo-client-maven-plugin</artifactId>
        <version>1.1.0</version>
        <executions>
            <execution>
                <goals>
                    <goal>generate</goal>
                </goals>
                <configuration>
                    <basePackage>com.my.package.graphql.client</basePackage>
                </configuration>
            </execution>
        </executions>
    </plugin>
    ```
3. Create a file `src/main/graphql/schema.json` with the JSON results of an [introspection query](https://gist.github.com/craigbeck/b90915d49fda19d5b2b17ead14dcd6da)
4. Create files for each query you'd like to generate classes for under `src/main/graphql`:
    1. Query file names must match the name of the query they contain
    2. Query files must end with `.graphql`
    3. Any subdirectories under `src/main/graphql` are treated as extra package names to append to `packageName` in the plugin config.
5. Run `mvn clean generate-sources` to generate classes for your queries.

### Configuration Options

All plugin options and their defaults:
```xml
<configuration>
    <outputDirectory>${project.build.directory}/generated-sources/graphql-client</outputDirectory>
    <basePackage>com.example.graphql.client</basePackage>
    <schemaFile>${project.basedir}/src/main/graphql/schema.json</schemaFile>
    <addSourceRoot>true</addSourceRoot>
    <customTypeMap></customTypeMap>
</configuration>
```

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
    
Optional<GetBooks.Data> data = client.newCall(new GetBooks()).execute().data()

if(data.isPresent()) {
    System.out.println("Book count: " + data.get().books().size());
}
```

Properties specified as nullable in the schema will have an java 8 `java.util.optional` type.
