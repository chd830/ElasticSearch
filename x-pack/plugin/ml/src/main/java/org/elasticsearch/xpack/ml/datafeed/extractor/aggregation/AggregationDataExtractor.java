/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.datafeed.extractor.aggregation;

import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * An implementation that extracts data from elasticsearch using search with aggregations on a client.
 * The first time {@link #next()} is called, the search is executed. The result aggregations are
 * stored and they are then processed in batches. Cancellation is supported between batches.
 * Note that this class is NOT thread-safe.
 */
class AggregationDataExtractor extends AbstractAggregationDataExtractor<SearchRequestBuilder> {

    AggregationDataExtractor(Client client, AggregationDataExtractorContext dataExtractorContext) {
        super(client, dataExtractorContext);
    }

    @Override
    protected SearchRequestBuilder buildSearchRequest(SearchSourceBuilder searchSourceBuilder) {

        return new SearchRequestBuilder(client, SearchAction.INSTANCE)
            .setSource(searchSourceBuilder)
            .setIndices(context.indices)
            .setTypes(context.types);
    }
}
