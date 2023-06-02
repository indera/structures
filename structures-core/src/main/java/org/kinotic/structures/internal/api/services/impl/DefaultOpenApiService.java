package org.kinotic.structures.internal.api.services.impl;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.text.WordUtils;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.services.StructureService;
import org.kinotic.structures.internal.api.services.OpenApiConversionResult;
import org.kinotic.structures.internal.api.services.OpenApiService;
import org.kinotic.structures.internal.api.services.StructureConversionService;
import org.kinotic.structures.internal.config.OpenApiSecurityType;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 3/17/23.
 */
@Component
public class DefaultOpenApiService implements OpenApiService {

    private final StructureService structureService;
    private final StructureConversionService structureConversionService;
    private final StructuresProperties structuresProperties;

    public DefaultOpenApiService(StructureService structureService,
                                 StructureConversionService structureConversionService,
                                 StructuresProperties structuresProperties) {
        this.structureService = structureService;
        this.structureConversionService = structureConversionService;
        this.structuresProperties = structuresProperties;
    }

    @Override
    public CompletableFuture<OpenAPI> getOpenApiSpec(String namespace) {
        return structureService.findAllPublishedForNamespace(namespace, Pageable.ofSize(100))
                               .thenComposeAsync(structures -> {
                                   OpenAPI openAPI = new OpenAPI();

                                   Info info = new Info()
                                           .title(namespace + " Structures API")
                                           .version("1.0")
                                           .description("Provides access to Structures Items for the " + namespace + " namespace");
                                   openAPI.setInfo(info);

                                   Components components = new Components();

                                   // security scheme
                                   if(structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BASIC){
                                       SecurityScheme securityScheme = new SecurityScheme();
                                       securityScheme.setType(SecurityScheme.Type.HTTP);
                                       securityScheme.setScheme("basic");
                                       components.addSecuritySchemes("BasicAuth", securityScheme);
                                       openAPI.setSecurity(List.of(new SecurityRequirement().addList("BasicAuth")));
                                   } else if (structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BEARER){
                                       SecurityScheme securityScheme = new SecurityScheme();
                                       securityScheme.setType(SecurityScheme.Type.HTTP);
                                       securityScheme.setScheme("bearer");
                                       components.addSecuritySchemes("BearerAuth", securityScheme);
                                       openAPI.setSecurity(List.of(new SecurityRequirement().addList("BearerAuth")));
                                   }

                                   Paths paths = new Paths();
                                   String basePath = structuresProperties.getOpenApiPath();

                                   for(Structure structure : structures.getContent()){
                                       // Add path items for the structure
                                       addPathItemsForStructure(paths, basePath, structure);

                                       OpenApiConversionResult result = structureConversionService.convertToOpenApiMapping(structure);

                                       components.addSchemas(structure.getName(), result.getObjectSchema());

                                       for(Map.Entry<String, Schema<?>> entry : result.getReferenceSchemas().entrySet()){
                                           components.addSchemas(entry.getKey(), entry.getValue());
                                       }
                                   }

                                   openAPI.setPaths(paths);
                                   openAPI.components(components);
                                   return CompletableFuture.completedFuture(openAPI);
                               });
    }

    public void addPathItemsForStructure(Paths paths, String basePath, Structure structure){

        String structureName = WordUtils.capitalize(structure.getName());

        // Create a path item for all the operations with basePath/structureNamespace/structureName/:id"
        PathItem byIdPathItem = new PathItem();

        // Operation for get by id
        Operation getByIdOperation = createOperation("Get "+structureName+" by Id",
                                                     "get"+structureName+"ById",
                                                     structure,
                                                     1);



        getByIdOperation.addParametersItem(new Parameter().name("id")
                                                          .in("path")
                                                          .required(true)
                                                          .schema(new StringSchema())
                                                          .description("The id of the "+structureName+" to get"));

        byIdPathItem.get(getByIdOperation);

        // Operation for delete
        Operation deleteOperation = createOperation("Delete "+structureName,
                                                    "delete"+structureName,
                                                    structure,
                                                    0);

        deleteOperation.addParametersItem(new Parameter().name("id")
                                                         .in("path")
                                                         .required(true)
                                                         .schema(new StringSchema())
                                                         .description("The id of the "+structureName+" to delete"));

        byIdPathItem.delete(deleteOperation);

        // add the path item to the paths
        paths.put(basePath + structure.getNamespace() + "/" + structure.getName() + "/{id}", byIdPathItem);


        // Create a path item for all the operations with basePath/structureNamespace/structureName/
        PathItem structurePathItem = new PathItem();

        // Request body for save operations
        Schema<?> refSchema = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName());
        RequestBody structureRequestBody = new RequestBody()
                .content(new Content().addMediaType("application/json",
                                                    new MediaType().schema(refSchema)));

        // Save Operation
        Operation createOperation = createOperation("Save"+structureName,
                                                    "save"+structureName,
                                                    structure,
                                                    1);
        createOperation.requestBody(structureRequestBody);

        structurePathItem.post(createOperation);

        // Find All Operation
        Operation getAllOperation = createOperation("Find all "+structureName,
                                                    "findAll"+structureName,
                                                    structure,
                                                    2);

        addPagingAndSortingParameters(getAllOperation);

        structurePathItem.get(getAllOperation);

        // add the path item to the paths
        paths.put(basePath + structure.getNamespace() + "/" + structure.getName(), structurePathItem);


        // Create a path item for all the operations with basePath/structureNamespace/structureName/search
        PathItem searchPathItem = new PathItem();
        Operation searchOperation = createOperation("Search "+structureName,
                                                    "search"+structureName,
                                                    structure,
                                                    2);

        addPagingAndSortingParameters(searchOperation);

        RequestBody searchRequestBody = new RequestBody()
                .content(new Content().addMediaType("text/plain",
                                                    new MediaType().schema(new StringSchema())));
        searchOperation.requestBody(searchRequestBody);

        searchPathItem.post(searchOperation);

        // add the path item to the paths
        paths.put(basePath + structure.getNamespace() + "/" + structure.getName() + "/search", searchPathItem);


//        // Create a path item for all the operations with "/api/"+structure.getId()+"/bulk-upsert"
//        PathItem bulkUpsertPathItem = new PathItem();
//        Operation bulkUpsertOperation = createOperation("Bulk Upsert "+structureName,
//                                                        "bulkUpsert"+structureName,
//                                                        structure,
//                                                        0);
//
//        ArraySchema bulkUpsertSchema = new ArraySchema();
//        bulkUpsertSchema.items(refSchema);
//        RequestBody bulkUpsertRequestBody = new RequestBody()
//                .content(new Content().addMediaType("application/json",
//                                                    new MediaType().schema(bulkUpsertSchema)));
//        bulkUpsertOperation.requestBody(bulkUpsertRequestBody);
//
//        bulkUpsertPathItem.post(bulkUpsertOperation);
//        paths.put(basePath + structure.getId()+"/bulk-upsert", bulkUpsertPathItem);
    }

    private void addPagingAndSortingParameters(Operation operation){
        operation.addParametersItem(new Parameter().name("page")
                                                   .in("query")
                                                   .required(false)
                                                   .schema(new IntegerSchema()._default(0))
                                                   .description("The page number to get. The first page is 0. The default is 0."));

        operation.addParametersItem(new Parameter().name("size")
                                                   .in("query")
                                                   .required(false)
                                                   .schema(new IntegerSchema()._default(25)
                                                                              .maximum(BigDecimal.valueOf(100)))
                                                   .description("The number of items per page. The default is 25."));

        operation.addParametersItem(new Parameter().name("sort")
                                                   .in("query")
                                                   .required(false)
                                                   .schema(new StringSchema())
                                                   .description("The field to apply sorting to."
                                                                        +  " Multiple sort fields can be applied by separating them with a comma."
                                                                        +  " The sort order for each sort field will be ascending unless it is prefixed with a minus (“-“), in which case it will be descending.")
                                                   .examples(Map.of("Single Sort", new Example().value("name"),
                                                                    "Multiple Sort", new Example().value("name,-age"))));
    }

    private Operation createOperation(String operationSummary,
                                      String operationId,
                                      Structure structure,
                                      int responseType) {

        Operation operation = new Operation().summary(operationSummary)
                                             .tags(List.of(structure.getName()))
                                             .operationId(operationId);

        if(structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BASIC){
            operation.security(List.of(new SecurityRequirement().addList("BasicAuth")));
        }else if(structuresProperties.getOpenApiSecurityType() == OpenApiSecurityType.BEARER){
            operation.security(List.of(new SecurityRequirement().addList("BearerAuth")));
        }

        // Add the default responses and the response for the structure item being returned
        ApiResponses defaultResponses = getDefaultResponses();

        // create a response for the structure item
        ApiResponse response = new ApiResponse().description(operationSummary + " OK");
        Content content = new Content();
        MediaType mediaType = new MediaType();
        if(responseType == 1){

            mediaType.setSchema(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName()));
            content.addMediaType("application/json", mediaType);
            response.setContent(content);

        }else if(responseType == 2){

            ObjectSchema pageSchema = new ObjectSchema();

            pageSchema.addProperty("content", new ArraySchema()
                    .items(new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + structure.getName())));

            pageSchema.addProperty("size", new IntegerSchema()
                    .description("The number of entities per page"));

            pageSchema.addProperty("totalElements", new IntegerSchema()
                    .description("The total number of entities"));

            mediaType.setSchema(pageSchema);
            content.addMediaType("application/json", mediaType);
            response.setContent(content);
        }

        defaultResponses.put("200", response);

        operation.setResponses(defaultResponses);

        return operation;
    }

    private static ApiResponses getDefaultResponses(){
        ApiResponses responses = new ApiResponses();
        responses.put("400", new ApiResponse().description("Bad Request"));
        responses.put("401", new ApiResponse().description("Unauthorized"));
        responses.put("403", new ApiResponse().description("Forbidden"));
        responses.put("404", new ApiResponse().description("Not Found"));
        responses.put("500", new ApiResponse().description("Internal Server Error"));
        return responses;
    }

}
