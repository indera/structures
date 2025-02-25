package org.kinotic.structures.internal.graphql;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A PreparsedDocumentProvider that caches the results of parsing and validating a query.
 * Created by Navíd Mitchell 🤪 on 4/17/23.
 */
public class CachingPreparsedDocumentProvider implements PreparsedDocumentProvider {

    private final AsyncCache<String, PreparsedDocumentEntry> cache  = Caffeine.newBuilder()
                                                                               .expireAfterWrite(10, TimeUnit.MINUTES)
                                                                               .maximumSize(500)
                                                                               .buildAsync();

    @Override
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                              Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return getDocumentAsync(executionInput, parseAndValidateFunction).join();
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput,
                                                                      Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        Function<String, PreparsedDocumentEntry> mapCompute = key -> parseAndValidateFunction.apply(executionInput);
        return cache.get(executionInput.getQuery(), mapCompute);
    }
}
