package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import java.util.concurrent.CompletableFuture

/**
 * @author Sparow199
 */
class TestCallbackFuture<T>(private val future: CompletableFuture<Response<T>>) : ApolloCall.Callback<T>() {

    override fun onFailure(ex: ApolloException) {
        future.completeExceptionally(ex)
    }

    override fun onResponse(response: Response<T>) {
        future.complete(response)
    }

}
