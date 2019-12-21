package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import java.util.concurrent.CompletableFuture

/**
 * @author AOUDIA Moncef
 */
fun <T> ApolloCall<T>.toCompletableFuture(): CompletableFuture<Response<T>> {
    val completableFuture = CompletableFuture<Response<T>>()

    completableFuture.whenComplete { _, _ ->
        if (completableFuture.isCancelled) {
            cancel()
        }
    }

    enqueue(object : ApolloCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
            completableFuture.complete(response)
        }

        override fun onFailure(e: ApolloException) {
            completableFuture.completeExceptionally(e)
        }
    })

    return completableFuture
}
