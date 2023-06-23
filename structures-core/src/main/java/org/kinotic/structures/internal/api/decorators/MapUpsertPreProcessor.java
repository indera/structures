package org.kinotic.structures.internal.api.decorators;

import org.apache.commons.lang3.tuple.Pair;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.structures.api.decorators.IdDecorator;
import org.kinotic.structures.api.decorators.runtime.crud.UpsertFieldPreProcessor;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public class MapUpsertPreProcessor implements UpsertPreProcessor<Map<Object, Object>, List<Map<Object, Object>>>{

    private static final Logger log = LoggerFactory.getLogger(MapUpsertPreProcessor.class);

    private final Structure structure;
    // Map of json path to decorator logic
    private final Map<String, DecoratorLogic> fieldPreProcessors;

    private Pair<String, DecoratorLogic> idFieldPreProcessor = null;

    public MapUpsertPreProcessor(Structure structure,
                                 Map<String, DecoratorLogic> fieldPreProcessors) {
        this.structure = structure;
        this.fieldPreProcessors = fieldPreProcessors;

        // this is a temporary solution since we only have an id field preprocessor now.
        for(Map.Entry<String, DecoratorLogic> entry : fieldPreProcessors.entrySet()) {
            if(entry.getValue().getDecorator() instanceof IdDecorator) {
                idFieldPreProcessor = Pair.of(entry.getKey(), entry.getValue());
                break;
            }
        }
        if(idFieldPreProcessor == null) {
            log.warn("No id field preprocessor found for structure: {}", structure);
        }
    }

    @Override
    public CompletableFuture<EntityHolder<Map<Object, Object>>> process(Map<Object, Object> entity,
                                                                        EntityContext context) {
        // ids are not allowed to be nested
        if(idFieldPreProcessor != null && !idFieldPreProcessor.getLeft().contains(".")){
            String id = processIdField(entity, context);
            return CompletableFuture.completedFuture(new EntityHolder<>(id, entity));
        }else{
            return CompletableFuture.failedFuture(new IllegalStateException("No id field found"));
        }
    }

    private String processIdField(Map<Object, Object> entity, EntityContext context) {
        String fieldName = idFieldPreProcessor.getLeft();
        Object fieldValue = entity.get(fieldName);
        DecoratorLogic decoratorLogic = idFieldPreProcessor.getRight();
        C3Decorator decorator = decoratorLogic.getDecorator();
        UpsertFieldPreProcessor<C3Decorator, Object, Object> preProcessor = decoratorLogic.getProcessor();
        // we know this is the IdDecorator, so we can cast it
        String id = (String) preProcessor.process(structure, fieldName, decorator, fieldValue, context);
        entity.put(fieldName, id);
        return id;
    }

    @Override
    public CompletableFuture<List<EntityHolder<Map<Object, Object>>>> processArray(List<Map<Object, Object>> entities,
                                                                                   EntityContext context) {
        // ids are not allowed to be nested
        if(idFieldPreProcessor != null && !idFieldPreProcessor.getLeft().contains(".")){
            List<EntityHolder<Map<Object, Object>>> entityHolders = new ArrayList<>();
            for(Map<Object, Object> entity : entities) {
                String id = processIdField(entity, context);
                entityHolders.add(new EntityHolder<>(id, entity));
            }
            return CompletableFuture.completedFuture(entityHolders);
        }else{
            return CompletableFuture.failedFuture(new IllegalStateException("No id field found"));
        }
    }
}
