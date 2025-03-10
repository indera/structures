package org.kinotic.structures.internal.api.services;

import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;

/**
 * Created by Navíd Mitchell 🤪 on 3/18/23.
 */
public interface OpenApiService {

    /**
     * Gets the OpenAPI spec for all the structures in the given namespace
     * @param namespace the namespace to get the OpenAPI spec for
     * @return the OpenAPI spec
     */
    OpenAPI getOpenApiSpec(String namespace) throws IOException;

}
