package com.jellio.android.data.books

// Shelf keys of books this reader removed from their own library ("i:<id>"),
// as normalised item ids. Other readers may still have the book on the server.
fun hiddenBookIds(libraryRemoved: List<String>): Set<String> =
    libraryRemoved
        .filter { it.startsWith("i:") }
        .map { BooksRepository.normalizeId(it.removePrefix("i:")) }
        .toSet()
