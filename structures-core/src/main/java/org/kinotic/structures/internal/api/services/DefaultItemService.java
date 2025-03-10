/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.structures.internal.api.services;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.kinotic.structures.api.domain.NotFoundException;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.Trait;
import org.kinotic.structures.api.domain.TypeCheckMap;
import org.kinotic.structures.api.services.ItemService;
import org.kinotic.structures.internal.api.services.util.BulkUpdate;
import org.kinotic.structures.internal.trait.TraitLifecycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

@Component
public class DefaultItemService implements ItemService, ItemServiceInternal { // TODO: after continuum fix remove ItemService

    private static final Logger log = LoggerFactory.getLogger(DefaultItemService.class);

    private final RestHighLevelClient highLevelClient;
    private final StructureServiceInternal structureService;
    private final TraitLifecycles traitLifecycles;
    private final ConcurrentHashMap<String, BulkUpdate> bulkRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> activeBulkRequests = new ConcurrentHashMap<>();

    public DefaultItemService(RestHighLevelClient highLevelClient,
                              StructureServiceInternal structureService,
                              TraitLifecycles traitLifecycles) {
        this.highLevelClient = highLevelClient;
        this.structureService = structureService;
        this.traitLifecycles = traitLifecycles;
    }

    @PreDestroy
    void cleanup() {
        // if we have any outstanding bulk requests, flush and close them.
        for (Map.Entry<String, BulkUpdate> entry : bulkRequests.entrySet()) {
            try {
                BulkProcessor processor = this.bulkRequests.remove(entry.getKey()).getBulkProcessor();
                processor.awaitClose(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("Encountered an error when trying to flush/close bulk update.", e);
            }
        }
    }

    @Override
    public TypeCheckMap upsertItem(String structureId, TypeCheckMap item, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        final Structure structure = optional.orElseThrow();// will throw null pointer/element not available
        if (!structure.isPublished()) {
            throw new IllegalStateException("'" + structure.getId() + "' Structure is not published and cannot have had Items modified for it");
        }

        // ensure required fields are present, system managed fields are automatically processed by hooks; so don't require them
        for (Map.Entry<String, Trait> traitEntry : structure.getTraits().entrySet()) {
            if (!traitEntry.getValue().isSystemManaged() && traitEntry.getValue()
                                                                      .isRequired() && !item.has(traitEntry.getKey())) {
                throw new IllegalStateException("'" + structure.getId() + "' Structure create/modify has been called without all required fields");
            }
        }

        // perform before create/update hooks - id is created if it does not already exist
        TypeCheckMap toUpsert = traitLifecycles.processBeforeModifyLifecycle(item, structure, context);

        // process upsert
        processUpdateRequest(structure, toUpsert, true);

        // get value fresh from db
        TypeCheckMap ret = getItemById(structureId, toUpsert.getString("id"), context).orElseThrow();

        return (TypeCheckMap) traitLifecycles.processAfterModifyLifecycle(ret, structure, context);
    }

    @Override
    public void requestBulkUpdatesForStructure(String structureId) throws IOException, NotFoundException {
        if (!this.bulkRequests.containsKey(structureId)) {
            Optional<Structure> structureOptional = this.structureService.getById(structureId);
            if (structureOptional.isEmpty()) {
                throw new NotFoundException("Not able to find requested Structure");
            }
            BiConsumer<BulkRequest, ActionListener<BulkResponse>> bulkConsumer =
                    (request, bulkListener) -> highLevelClient.bulkAsync(request, RequestOptions.DEFAULT, bulkListener);
            BulkProcessor bulkProcessor = BulkProcessor.builder(bulkConsumer, new BulkProcessor.Listener() {
                        private final AtomicLong count = new AtomicLong(0);

                        @Override
                        public void beforeBulk(long executionId,
                                               BulkRequest request) {
                        }

                        @Override
                        public void afterBulk(long executionId,
                                              BulkRequest request,
                                              BulkResponse response) {
                            if (response.hasFailures()) {
                                for (BulkItemResponse itemResponse : response.getItems()) {
                                    log.error("DefaultItemService: Encountered an error while ingesting data.  for Structure: '" + structureId + "'    Index: " + itemResponse.getIndex() + " \n\r    " + itemResponse.getFailureMessage(),
                                            itemResponse.getFailure());
                                }
                            }

                            long currentCount = count.addAndGet(request.numberOfActions());
                            log.debug("DefaultItemService: bulk processing for Structure '" + structureId + "' finished indexing : " + currentCount);
                        }

                        @Override
                        public void afterBulk(long executionId,
                                              BulkRequest request,
                                              Throwable failure) {
                            log.error("DefaultItemService: Bulk Ingestion encountered an error. ", failure);
                        }
                    })
                   .setFlushInterval(TimeValue.timeValueSeconds(60))
                   .setBulkActions(2500)
                   .build();

            this.bulkRequests.put(structureId, new BulkUpdate(bulkProcessor, structureOptional.get()));
            this.activeBulkRequests.put(structureId, new AtomicLong(1));
        } else {
            AtomicLong number = this.activeBulkRequests.get(structureId);
            number.addAndGet(1);
            this.activeBulkRequests.put(structureId, number);
        }
    }

    @Override
    public void pushItemForBulkUpdate(String structureId, TypeCheckMap item, Map<String, Object> context) throws Exception {
        Assert.isTrue(structureId != null && !structureId.isBlank(), "Must provide valid structureId.");
        Assert.isTrue(this.bulkRequests.containsKey(structureId),
                      "Your structure not set up for bulk processing, please request new bulk updates for structure");

        //FIXME: what if the structure changes mid bulk update?
        BulkUpdate bulkUpdate = this.bulkRequests.get(structureId);
        for (Map.Entry<String, Trait> traitEntry : bulkUpdate.getStructure().getTraits().entrySet()) {
            if (!traitEntry.getValue().isSystemManaged() && traitEntry.getValue()
                                                                      .isRequired() && !item.has(traitEntry.getKey())) {
                throw new IllegalStateException("'" + structureId + "' Structure create/modify has been called without all required fields '"+traitEntry.getKey()+"'");
            }
        }

        TypeCheckMap ret = traitLifecycles.processBeforeModifyLifecycle(item, bulkUpdate.getStructure(), context);

        UpdateRequest request = new UpdateRequest(bulkUpdate.getStructure().getItemIndex(), item.getString("id"));
        request.docAsUpsert(true);
        request.doc(item, XContentType.JSON);

        this.bulkRequests.get(structureId).getBulkProcessor().add(request);

        traitLifecycles.processAfterModifyLifecycle(ret, bulkUpdate.getStructure(), context);

    }

    @Override
    public void flushAndCloseBulkUpdate(String structureId) throws Exception {
        Assert.isTrue(structureId != null && !structureId.isBlank(), "Must provide valid structureId.");
        Assert.isTrue(this.bulkRequests.containsKey(structureId),
                      "Your structure not set up for bulk processing, please request new bulk update for structure.");
        if (this.activeBulkRequests.get(structureId).get() == 1) {
            this.bulkRequests.get(structureId).getBulkProcessor().awaitClose(30, TimeUnit.SECONDS);
            this.bulkRequests.remove(structureId);
            this.activeBulkRequests.remove(structureId);
        } else {
            // current bulk updates will continue to work, any items pushed by closing process
            // will be processed at the next threshold or interval
            AtomicLong number = this.activeBulkRequests.get(structureId);
            number.addAndGet(-1);
            this.activeBulkRequests.put(structureId, number);
        }
    }

    @Override
    public long count(String structureId, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.size(0);
        if(queryBuilder.hasClauses()){
            builder.query(queryBuilder);
        }
        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(builder);
        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits().getTotalHits().value;
    }

    @Override
    public Optional<TypeCheckMap> getById(Structure structure, String id, Map<String, Object> context) throws Exception {
        GetResponse response = highLevelClient.get(new GetRequest(structure.getItemIndex()).id(id),
                                                   RequestOptions.DEFAULT);

        // LOOK: We can restrict access by adding a AfterGet lifecycle trait to a structure - but is there another way?

        TypeCheckMap ret = null;
        if (response.isExists()) {
            ret = new TypeCheckMap(response.getSourceAsMap());
            ret = traitLifecycles.processAfterGetLifecycle(ret, structure, context);
        }

        return Optional.ofNullable(ret);
    }

    /**
     * This function will act ast the ObjectReference Resolver function.  The ObjectReference will already
     * have the structureName, which we use as the index name in ES.  This means we don't have to do a
     * $Structure lookup, just the item lookup.
     */
    @Override
    public Optional<TypeCheckMap> getItemById(String structureId, String id, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        return getById(structure, id, context);
    }

    /**
     * Below are the SearchHits functions, we do not attempt any reference resolution b/c we want to
     * lazy load any references when the user decides they want to view a single item.  The JavaScript
     * side should be able to know when it needs to resolve a reference object, it will have a specific
     * structure to it.. please see ObjectReference trait lifecycle for more information on structure.
     */
    @Override
    public SearchHits searchForItemsById(String structureId, Map<String, Object> context, String... ids) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(new IdsQueryBuilder().addIds(ids));

        if(queryBuilder.hasClauses()){
            builder.postFilter(queryBuilder);
        }

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(builder);

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits();
    }

    @Override
    public SearchHits getAll(String structureId, int numberPerPage, int from, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        SearchSourceBuilder builder = new SearchSourceBuilder()
                                            .from(from * numberPerPage)
                                            .size(numberPerPage);
        if(queryBuilder.hasClauses()){
            builder.query(queryBuilder);
        }

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(builder);

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits();
    }


    /**
     * Provides a terms search functionality, a keyword type search over provided fields.
     * <p>
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/6.2/query-dsl-terms-query.html">Terms Query</a>
     */
    @Override
    public SearchHits searchTerms(String structureId,
                                  int numberPerPage,
                                  int from,
                                  String fieldName,
                                  Map<String, Object> context,
                                  Object... searchTerms) throws Exception {

        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        queryBuilder.filter(QueryBuilders.termsQuery(fieldName, searchTerms));

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(new SearchSourceBuilder()
                               .query(queryBuilder)
                               .from(from * numberPerPage)
                               .size(numberPerPage));

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits();
    }

    /**
     * Provides a multisearch functionality, a full text search type.
     * <p>
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/6.2/query-dsl-multi-match-query.html">Multi Match Query</a>
     */
    @Override
    public SearchHits searchFullText(String structureId,
                                     int numberPerPage,
                                     int from,
                                     String search,
                                     Map<String, Object> context,
                                     String... fieldNames) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        queryBuilder.filter(QueryBuilders.multiMatchQuery(search, fieldNames));

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(new SearchSourceBuilder()
                               .query(queryBuilder)
                               .from(from * numberPerPage)
                               .size(numberPerPage));

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits();
    }

    /**
     * Provides an option for expert level searching, using standard lucene query structure.
     * <p>
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/6.2/query-dsl-query-string-query.html">String Query</a>
     */
    @Override
    public SearchHits search(String structureId, String search, int numberPerPage, int from, Map<String, Object> context) throws Exception {
        return search(structureId, search, numberPerPage, from, null, null, context);
    }

    @Override
    public SearchHits search(String structureId,
                             String search,
                             int numberPerPage,
                             int from,
                             String sortField,
                             SortOrder sortOrder,
                             Map<String, Object> context) throws Exception {

        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        SearchSourceBuilder builder = new SearchSourceBuilder()
                .query(new QueryStringQueryBuilder(search))
                .from(from * numberPerPage)
                .size(numberPerPage);

        if(queryBuilder.hasClauses()){
            builder.postFilter(queryBuilder);
        }

        if (sortField != null) {
            builder.sort(sortField, sortOrder);
        }

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(builder);

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        return response.getHits();
    }

    @Override
    public SearchHits searchWithSort(String structureId,
                                     String search,
                                     int numberPerPage,
                                     int from,
                                     String sortField,
                                     boolean descending,
                                     Map<String, Object> context) throws Exception {
        return this.search(structureId,
                           search,
                           numberPerPage,
                           from,
                           sortField,
                           descending ? SortOrder.DESC : SortOrder.ASC,
                           context);
    }

    @Override
    public List<String> searchDistinct(String structureId, String search, String field, int limit, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();// will throw null pointer/element not available

        BoolQueryBuilder queryBuilder = traitLifecycles.processBeforeSearchLifecycle(new BoolQueryBuilder(), structure, context);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                                                    .aggregation(AggregationBuilders.terms(field).field(field).size(500))
                                                    .query(new QueryStringQueryBuilder(search))
                                                    .size(limit);

        if(queryBuilder.hasClauses()){
            sourceBuilder.postFilter(queryBuilder);
        }

        SearchRequest request = new SearchRequest(structure.getItemIndex());
        request.source(sourceBuilder);

        SearchResponse response = highLevelClient.search(request, RequestOptions.DEFAULT);

        ArrayList<String> keys = new ArrayList<>();
        Terms byCoach = response.getAggregations().get(field);
        for (Terms.Bucket bucket : byCoach.getBuckets()) {
            keys.add(bucket.getKeyAsString());
        }
        return keys;
    }

    @Override
    public void delete(String structureId, String itemId, Map<String, Object> context) throws Exception {
        Optional<Structure> optional = structureService.getById(structureId);
        Structure structure = optional.orElseThrow();

        // if document level security is in use, the getById will validate access
        TypeCheckMap item = getById(structure, itemId, context).orElseThrow();
        TypeCheckMap ret = traitLifecycles.processBeforeDeleteLifecycle(item, structure, context);

        processUpdateRequest(structure, ret, false);

        //TODO: find out how this will operate concurrently
        traitLifecycles.processAfterDeleteLifecycle(ret, structure, context);
    }

    private void processUpdateRequest(Structure structure, TypeCheckMap ret, boolean asUpsert) throws IOException {
        UpdateRequest request = new UpdateRequest(structure.getItemIndex(), ret.getString("id"));
        request.docAsUpsert(asUpsert);
        request.doc(ret, XContentType.JSON);
        // forces a cluster refresh of the index.. for high volume data this wouldn't work - lets see how it works in our case.
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        highLevelClient.update(request, RequestOptions.DEFAULT);
    }

}
