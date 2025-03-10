package org.kinotic.structures.internal.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.kinotic.continuum.api.jsonSchema.JsonSchema;
import org.kinotic.continuum.api.jsonSchema.*;
import org.kinotic.continuum.api.jsonSchema.datestyles.MillsDateStyle;
import org.kinotic.continuum.api.jsonSchema.datestyles.StringDateStyle;
import org.kinotic.continuum.api.jsonSchema.datestyles.UnixDateStyle;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.StructureHolder;
import org.kinotic.structures.api.domain.Structures;
import org.kinotic.structures.api.domain.Trait;
import org.kinotic.structures.internal.config.OpenApiSecurityType;
import org.kinotic.structures.internal.config.StructuresProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 3/17/23.
 */
@Component
public class DefaultOpenApiService implements OpenApiService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOpenApiService.class);

    private final ObjectMapper objectMapper;
    private final StructureServiceInternal structureService;
    private final StructuresProperties structuresProperties;

    public DefaultOpenApiService(ObjectMapper objectMapper,
                                 StructureServiceInternal structureService,
                                 StructuresProperties structuresProperties) {
        this.objectMapper = objectMapper;
        this.structureService = structureService;
        this.structuresProperties = structuresProperties;
    }

    @Override
    public OpenAPI getOpenApiSpec(String namespace) throws IOException {
        OpenAPI openAPI = new OpenAPI();

        Info info = new Info()
                .title(namespace + " Structures API")
                .version("1.0")
                .description("Provides access to Structures Items for the " + namespace + " namespace");
        openAPI.setInfo(info);

//        List<Server> servers = new ArrayList<>();
//        servers.add(new Server().url("http://127.0.0.1:8090"));
//        openAPI.setServers(servers);

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

        Structures structures = structureService.getAllPublishedForNamespace(namespace, 100, 0, "name", false);
        Paths paths = new Paths();
        for(StructureHolder structureHolder : structures.getContent()){
            Structure structure = structureHolder.getStructure();
            // Add path items for the structure
            addPathItemsForStructure(paths, structure);

            //Now Add Schemas for the structure, one with all fields and one with only the input fields
            Schema<?> schema = getSchemaForStructureItem(structure, false);
            components.addSchemas(structure.getName(), schema);

            Schema<?> schemaInput = getSchemaForStructureItem(structure, true);
            components.addSchemas(structure.getName()+"Input", schemaInput);
        }
        openAPI.setPaths(paths);
        openAPI.components(components);

        return openAPI;
    }

    public void addPathItemsForStructure(Paths paths, Structure structure){

        // Create a path item for all the operations with "/api/"+structure.getId()
        PathItem structurePathItem = new PathItem();

        Operation getAllOperation = createOperation("Get all "+structure.getName(),
                                                    "getAll"+structure.getName(),
                                                    structure.getName(),
                                                    2);

        getAllOperation.addParametersItem(new Parameter().name("page")
                                                         .in("query")
                                                         .description("The page number to get")
                                                         .required(false)
                                                         .schema(new IntegerSchema()._default(0)));

        getAllOperation.addParametersItem(new Parameter().name("size")
                                                         .in("query")
                                                         .description("The number of items per page")
                                                         .required(false)
                                                         .schema(new IntegerSchema()._default(25)));

        structurePathItem.get(getAllOperation);

        // Request body for upsert operations
        Schema<?> refSchema = new Schema<>().$ref(structure.getName()+"Input");
        RequestBody structureRequestBody = new RequestBody()
                .content(new Content().addMediaType("application/json",
                                                    new MediaType().schema(refSchema)));

        // Operation for create
        Operation createOperation = createOperation("Upsert "+structure.getName(),
                                                    "upsert"+structure.getName(),
                                                    structure.getName(),
                                                    1);
        createOperation.requestBody(structureRequestBody);

        structurePathItem.post(createOperation);

        paths.put("/api/"+structure.getId(), structurePathItem);


        // Create a path item for all the operations with "/api/"+structure.getId()+"/{id}"
        PathItem byIdPathItem = new PathItem();

        // Operation for get by id
        Operation getByIdOperation = createOperation("Get "+structure.getName()+" by Id",
                                                     "get"+structure.getName()+"ById",
                                                     structure.getName(),
                                                     1);

        getByIdOperation.addParametersItem(new Parameter().name("id")
                                                          .in("path")
                                                          .description("The id of the "+structure.getName()+" to get")
                                                          .required(true)
                                                          .schema(new StringSchema()));

        byIdPathItem.get(getByIdOperation);

        // Operation for delete
        Operation deleteOperation = createOperation("Delete "+structure.getName(),
                                                    "delete"+structure.getName(),
                                                    structure.getName(),
                                                    0);

        deleteOperation.addParametersItem(new Parameter().name("id")
                                                         .in("path")
                                                         .description("The id of the "+structure.getName()+" to delete")
                                                         .required(true)
                                                         .schema(new StringSchema()));

        byIdPathItem.delete(deleteOperation);

        paths.put("/api/"+structure.getId()+"/{id}", byIdPathItem);

        // Create a path item for all the operations with "/api/"+structure.getId()+"/search"
        PathItem searchPathItem = new PathItem();
        Operation searchOperation = createOperation("Search "+structure.getName(),
                                                    "search"+structure.getName(),
                                                    structure.getName(),
                                                    2);

        searchOperation.addParametersItem(new Parameter().name("page")
                                                         .in("query")
                                                         .description("The page number to get")
                                                         .required(false)
                                                         .schema(new IntegerSchema()._default(0)));

        searchOperation.addParametersItem(new Parameter().name("size")
                                                         .in("query")
                                                         .description("The number of items per page")
                                                         .required(false)
                                                         .schema(new IntegerSchema()._default(25)));

        RequestBody searchRequestBody = new RequestBody()
                .content(new Content().addMediaType("text/plain",
                                                    new MediaType().schema(new StringSchema())));
        searchOperation.requestBody(searchRequestBody);

        searchPathItem.post(searchOperation);
        paths.put("/api/"+structure.getId()+"/search", searchPathItem);


        // Create a path item for all the operations with "/api/"+structure.getId()+"/searchWithSort"
        PathItem searchWithSortPathItem = new PathItem();
        Operation searchWithSortOperation = createOperation("Search with Sort "+structure.getName(),
                "searchWithSort"+structure.getName(),
                structure.getName(),
                2);

        searchWithSortOperation.addParametersItem(new Parameter().name("sortField")
                .in("query")
                .description("The field to apply sorting to")
                .required(true)
                .schema(new StringSchema()));

        searchWithSortOperation.addParametersItem(new Parameter().name("isDescending")
                .in("query")
                .description("Should we sort descending")
                .required(false)
                .schema(new BooleanSchema()._default(false)));

        searchWithSortOperation.addParametersItem(new Parameter().name("page")
                .in("query")
                .description("The page number to get")
                .required(false)
                .schema(new IntegerSchema()._default(0)));

        searchWithSortOperation.addParametersItem(new Parameter().name("size")
                .in("query")
                .description("The number of items per page")
                .required(false)
                .schema(new IntegerSchema()._default(25)));

        RequestBody searchWithSortRequestBody = new RequestBody()
                .content(new Content().addMediaType("text/plain",
                        new MediaType().schema(new StringSchema())));
        searchWithSortOperation.requestBody(searchWithSortRequestBody);

        searchWithSortPathItem.post(searchWithSortOperation);
        paths.put("/api/"+structure.getId()+"/searchWithSort", searchWithSortPathItem);



        // Create a path item for all the operations with "/api/"+structure.getId()+"/bulk-upsert"
        PathItem bulkUpsertPathItem = new PathItem();
        Operation bulkUpsertOperation = createOperation("Bulk Upsert "+structure.getName(),
                                                        "bulkUpsert"+structure.getName(),
                                                        structure.getName(),
                                                        0);

        ArraySchema bulkUpsertSchema = new ArraySchema();
        bulkUpsertSchema.items(refSchema);
        RequestBody bulkUpsertRequestBody = new RequestBody()
                .content(new Content().addMediaType("application/json",
                                                    new MediaType().schema(bulkUpsertSchema)));
        bulkUpsertOperation.requestBody(bulkUpsertRequestBody);

        bulkUpsertPathItem.post(bulkUpsertOperation);
        paths.put("/api/"+structure.getId()+"/bulk-upsert", bulkUpsertPathItem);
    }

    private Operation createOperation(String operationSummary,
                                      String operationId,
                                      String structureName,
                                      int responseType) {

        Operation operation = new Operation().summary(operationSummary)
                                             .tags(List.of(structureName))
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
            mediaType.setSchema(new Schema<>().$ref(structureName));
            content.addMediaType("application/json", mediaType);
            response.setContent(content);
        }else if(responseType == 2){
            ObjectSchema searchHitsSchema = new ObjectSchema();
            searchHitsSchema.addProperty("content", new ArraySchema().items(new Schema<>().$ref(structureName)));
            searchHitsSchema.addProperty("totalElements", new IntegerSchema());
            mediaType.setSchema(searchHitsSchema);
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


    /**
     * This gets the Schema for a structure item.
     * This is not the schema for the structure itself but rather the schema for the items that are defined by the structure.
     *
     * @param structure to get the schema for
     * @return the schema for the structure item
     */
    public Schema<?> getSchemaForStructureItem(Structure structure, boolean excludeSystemTraits){
        ObjectSchema objectSchema = new ObjectSchema();
        for (Map.Entry<String, Trait> traitEntry : structure.getTraits().entrySet()) {
            if(!traitEntry.getValue().isSystemManaged() || (traitEntry.getValue().isSystemManaged() && !excludeSystemTraits)) {
                try {
                    Schema<?> schema = getSchemaForTrait(traitEntry.getValue());
                    if (schema != null) {
                        objectSchema.addProperty(traitEntry.getKey(), getSchemaForTrait(traitEntry.getValue()));
                    } else {
                        log.warn("Could not create OpenAPI schema for trait " + traitEntry.getKey() + ", skipping");
                    }

                    if (traitEntry.getValue().isRequired()) {
                        objectSchema.addRequiredItem(traitEntry.getKey());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get schema for trait " + traitEntry.getKey(), e);
                }
            }
        }
        return objectSchema;
    }

    /**
     * Gets the {@link Schema} that represents the given {@link Trait}
     *
     * @param trait to get the schema for
     * @return the schema for the trait
     * @throws Exception if there is an error getting the schema
     */
    public Schema<?> getSchemaForTrait(Trait trait) throws Exception{
        JsonSchema schema = objectMapper.readValue(trait.getSchema(), JsonSchema.class);
        Schema<?> ret = getSchemaForContinuumJsonSchema(schema);
        if(trait.isCollection()){
            ret = new ArraySchema().items(ret);
        }
        if(trait.getDescribeTrait() != null){
            ret.setDescription(trait.getDescribeTrait());
        }
        return ret;
    }

    private Schema<?> getSchemaForContinuumJsonSchema(JsonSchema schema){
        Schema<?> ret;
        if(schema instanceof DateJsonSchema){
            DateJsonSchema dateJsonSchema = (DateJsonSchema) schema;
            if(dateJsonSchema.getFormat() instanceof UnixDateStyle) {
                ret = new IntegerSchema().format("int64");
            }else if(dateJsonSchema.getFormat() instanceof MillsDateStyle) {
                ret = new IntegerSchema().format("int64");
            }else if(dateJsonSchema.getFormat() instanceof StringDateStyle) {
                StringDateStyle stringDateStyle = (StringDateStyle) dateJsonSchema.getFormat();
                ret = new StringSchema().pattern(stringDateStyle.getPattern());
            }else{
                throw new RuntimeException("Unknown date format " + dateJsonSchema.getFormat().getClass().getName());
            }
        }else if(schema instanceof StringJsonSchema) {
            StringJsonSchema stringJsonSchema = (StringJsonSchema) schema;
            ret = new StringSchema();
            if (stringJsonSchema.getMinLength().isPresent()) {
                ret.setMinLength(stringJsonSchema.getMinLength().get());
            }
            if (stringJsonSchema.getMaxLength().isPresent()) {
                ret.setMaxLength(stringJsonSchema.getMaxLength().get());
            }
            if (stringJsonSchema.getPattern().isPresent()) {
                ret.setPattern(stringJsonSchema.getPattern().get());
            }
        }else if(schema instanceof NumberJsonSchema) {
            NumberJsonSchema numberJsonSchema = (NumberJsonSchema) schema;
            ret = new NumberSchema();
            if (numberJsonSchema.getMinimum().isPresent()) {
                ret.setMinimum(BigDecimal.valueOf(numberJsonSchema.getMinimum().get()));
            }
            if (numberJsonSchema.getMaximum().isPresent()) {
                Float max = numberJsonSchema.getMaximum().get();
                if(max.isInfinite()){
                    ret.setMaximum(BigDecimal.valueOf(Double.MAX_VALUE));
                }else{
                    ret.setMaximum(BigDecimal.valueOf(numberJsonSchema.getMaximum().get()));
                }
            }
        }else if(schema instanceof BooleanJsonSchema){
            ret = new BooleanSchema();
        }else{
            throw new RuntimeException("Unknown schema type " + schema.getClass().getName());
        }

        return ret;
    }


}
