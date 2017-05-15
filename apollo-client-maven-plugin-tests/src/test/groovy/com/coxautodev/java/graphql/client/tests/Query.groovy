package com.coxautodev.java.graphql.client.tests

import com.coxautodev.graphql.tools.GraphQLRootResolver

/**
 * @author Andrew Potter
 */
class Query implements GraphQLRootResolver {

    private List<Book> books = []
    private List<Author> authors = []

    Query() {
        def dickens = new Author(name: "Charles Dickens")
        def twain = new Author(name: "Mark Twain")

        authors.addAll(dickens, twain)
        books.addAll(
            new Book(title: "A Christmas Carol", author: dickens),
            new Book(title: "David Copperfield", author: dickens),
            new Book(title: "The Adventures of Tom Sawyer", author: twain),
            new Book(title: "Adventures of Huckleberry Finn", author: twain),
        )
    }

    List<Book> getBooks() {
        return books
    }

    List<Author> getAuthors() {
        return authors
    }

    static class Book {
        private String title
        private Author author

        String getTitle() {
            return title
        }

        Author getAuthor() {
            return author
        }
    }

    static class Author {
        private String name

        String getName() {
            return name
        }
    }
}
