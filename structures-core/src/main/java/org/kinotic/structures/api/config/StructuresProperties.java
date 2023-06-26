package org.kinotic.structures.api.config;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.structures.internal.utils.StructuresUtil;
import org.kinotic.structures.internal.config.ElasticConnectionInfo;
import org.kinotic.structures.internal.config.OpenApiSecurityType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "structures")
public class StructuresProperties {

    @NotNull
    private String indexPrefix = "struct_";
    @NotNull
    private String tenantIdFieldName = "structuresTenantId";
    @NotNull
    private Duration elasticConnectionTimeout = Duration.ofMinutes(1);
    @NotNull
    private Duration elasticSocketTimeout = Duration.ofMinutes(1);
    @NotNull
    private List<ElasticConnectionInfo> elasticConnections = List.of(new ElasticConnectionInfo());

    private String elasticUsername = null;
    private String elasticPassword = null;

    private OpenApiSecurityType openApiSecurityType = OpenApiSecurityType.NONE;

    private int openApiPort = 8080;

    private String openApiPath = "/api/";

    private String openApiServerUrl = "http://localhost:8080";

    private int graphqlPort = 4000;

    private String graphqlPath = "/graphql/";


    /**
     * The allowed origin pattern for CORS
     * Defaults to "http://localhost"
     * If you want to allow all origins use "*"
     * Internally uses Java Regex Patterns to match
     * @see java.util.regex.Pattern
     */
    private String corsAllowedOriginPattern = "http://localhost.*";

    /**
     * If true will initialize the Structures with sample data
     */
    private boolean initializeWithSampleData = false;

    @PostConstruct
    public void validate(){
        // this will validate we do not contain invalid characters
        // FIXME: should we limit the number of chars as well?
        StructuresUtil.indexNameValidation(indexPrefix);
    }

    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() &&  elasticPassword != null && !elasticPassword.isBlank();
    }

    public StructuresProperties setOpenApiPath(String path){
        Validate.notBlank(path, "openApiPath must not be blank");
        if(path.endsWith("/")){
            this.openApiPath = path;
        }else{
            this.openApiPath = path + "/";
        }
        return this;
    }

    public StructuresProperties setGraphqlPath(String path) {
        Validate.notBlank(path, "graphqlPath must not be blank");
        if(path.endsWith("/")){
            this.graphqlPath = path;
        }else{
            this.graphqlPath = path + "/";
        }
        return this;
    }
}
