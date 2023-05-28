package org.kinotic.structures.api.decorators.runtime;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.structures.api.domain.Structure;

/**
 * {@link ElasticMappingPreProcessor} is used to create an ElasticSearch mapping based on data available in the {@link C3Decorator}
 * Created by Navíd Mitchell 🤪 on 5/12/23.
 */
public interface ElasticMappingPreProcessor<D extends C3Decorator, T extends C3Type> extends MappingPreProcessor<D,T, Property> {

    /**
     * Process the given {@link Structure} and {@link C3Decorator} to create or modify an ElasticSearch mapping
     * @param structure the {@link Structure} that is being processed
     * @param fieldName the name of the field that is being processed or null if this is a root level mapping
     * @param decorator the {@link C3Decorator} that is being processed
     * @param type the {@link C3Type} that is being processed
     * @param context the {@link MappingContext} for use by this {@link ElasticMappingPreProcessor}
     * @return the {@link Property} that was created or modified
     */
    @Override
    Property process(Structure structure,
                     String fieldName,
                     D decorator,
                     T type,
                     MappingContext<Property> context);

}
