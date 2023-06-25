package org.kinotic.structures.internal.api.services.impl;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.GraphQL;
import org.apache.commons.lang3.Validate;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.internal.api.services.GraphQLProviderService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
@Component
public class DefaultGraphQLProviderService implements GraphQLProviderService {

    private final AsyncLoadingCache<String, GraphQL> graphQLCache;

    public DefaultGraphQLProviderService(GraphQLCacheLoader graphQLCacheLoader) {
        graphQLCache = Caffeine.newBuilder()
                               .expireAfterAccess(20, TimeUnit.HOURS)
                               .maximumSize(10_000)
                               .buildAsync(graphQLCacheLoader);
    }

    @Override
    public CompletableFuture<GraphQL> getOrCreateGraphQL(String namespace) {
        return graphQLCache.get(namespace);
    }

    @Override
    public void evictCachesFor(Structure structure) {
        Validate.notNull(structure, "structure must not be null");
        graphQLCache.asMap().remove(structure.getNamespace());
    }
}
