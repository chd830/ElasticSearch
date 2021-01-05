/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.deprecation;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.xpack.core.deprecation.DeprecationInfoAction;
import org.elasticsearch.xpack.core.deprecation.DeprecationIssue;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static java.util.Collections.singletonList;
import static org.elasticsearch.xpack.deprecation.DeprecationChecks.INDEX_SETTINGS_CHECKS;

public class IndexDeprecationChecksTests extends ESTestCase {

    public void testOldIndicesCheck() {
        Version createdWith = VersionUtils.randomVersionBetween(random(), Version.V_5_0_0,
            VersionUtils.getPreviousVersion(Version.V_6_0_0));
        IndexMetaData indexMetaData = IndexMetaData.builder("test")
            .settings(settings(createdWith))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "Index created before 6.0",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/" +
                "breaking-changes-7.0.html",
            "this index was created using version: " + createdWith);
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(indexMetaData));
        assertEquals(singletonList(expected), issues);
    }

    public void testDelimitedPayloadFilterCheck() {
        Settings settings = settings(
            VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, VersionUtils.getPreviousVersion(Version.CURRENT)))
            .put("index.analysis.filter.my_delimited_payload_filter.type", "delimited_payload_filter")
            .put("index.analysis.filter.my_delimited_payload_filter.delimiter", "^")
            .put("index.analysis.filter.my_delimited_payload_filter.encoding", "identity").build();
        IndexMetaData indexMetaData = IndexMetaData.builder("test").settings(settings).numberOfShards(1).numberOfReplicas(0).build();
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING, "Use of 'delimited_payload_filter'.",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_literal_delimited_payload_filter_literal_renaming",
            "[The filter [my_delimited_payload_filter] is of deprecated 'delimited_payload_filter' type. "
                + "The filter type should be changed to 'delimited_payload'.]");
        List<DeprecationIssue> issues = DeprecationInfoAction.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(indexMetaData));
        assertEquals(singletonList(expected), issues);
    }

    public void testIndexNameCheck(){
        final String badIndexName = randomAlphaOfLengthBetween(0, 10) + ":" + randomAlphaOfLengthBetween(0, 10);
        final IndexMetaData badIndex = IndexMetaData.builder(badIndexName)
            .settings(settings(Version.CURRENT))
            .numberOfShards(randomIntBetween(1,100))
            .numberOfReplicas(randomIntBetween(1,15))
            .build();

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING, "Index name cannot contain ':'",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_literal_literal_is_no_longer_allowed_in_index_name",
            "This index is named [" + badIndexName + "], which contains the illegal character ':'.");
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(badIndex));
        assertEquals(singletonList(expected), issues);

        final String goodIndexName = randomAlphaOfLengthBetween(1,30);
        final IndexMetaData goodIndex = IndexMetaData.builder(goodIndexName)
            .settings(settings(Version.CURRENT))
            .numberOfShards(randomIntBetween(1,100))
            .numberOfReplicas(randomIntBetween(1,15))
            .build();
        List<DeprecationIssue> noIssues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(goodIndex));
        assertTrue(noIssues.isEmpty());
    }

    public void testPercolatorUnmappedFieldsAsStringCheck() {
        boolean settingValue = randomBoolean();
        Settings settings = settings(
            VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, VersionUtils.getPreviousVersion(Version.CURRENT)))
            .put("index.percolator.map_unmapped_fields_as_text", settingValue).build();
        final IndexMetaData badIndex = IndexMetaData.builder(randomAlphaOfLengthBetween(1,30).toLowerCase(Locale.ROOT))
            .settings(settings)
            .numberOfShards(randomIntBetween(1,100))
            .numberOfReplicas(randomIntBetween(1,15))
            .build();

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "Setting index.percolator.map_unmapped_fields_as_text has been renamed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_percolator",
            "The index setting [index.percolator.map_unmapped_fields_as_text] currently set to [" + settingValue +
                "] been removed in favor of [index.percolator.map_unmapped_fields_as_text].");
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(badIndex));
        assertEquals(singletonList(expected), issues);

        final IndexMetaData goodIndex = IndexMetaData.builder(randomAlphaOfLengthBetween(1,30).toLowerCase(Locale.ROOT))
            .settings(settings(Version.CURRENT))
            .numberOfShards(randomIntBetween(1,100))
            .numberOfReplicas(randomIntBetween(1,15))
            .build();
        List<DeprecationIssue> noIssues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(goodIndex));
        assertTrue(noIssues.isEmpty());
    }

    public void testClassicSimilarityMappingCheck() throws IOException {
        String mappingJson = "{\n" +
            "  \"properties\": {\n" +
            "    \"default_field\": {\n" +
            "      \"type\": \"text\"\n" +
            "    },\n" +
            "    \"classic_sim_field\": {\n" +
            "      \"type\": \"text\",\n" +
            "      \"similarity\": \"classic\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
        IndexMetaData index = IndexMetaData.builder(randomAlphaOfLengthBetween(5,10))
            .settings(settings(
                VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, VersionUtils.getPreviousVersion(Version.CURRENT))))
            .numberOfShards(randomIntBetween(1,100))
            .numberOfReplicas(randomIntBetween(1, 100))
            .putMapping("_doc", mappingJson)
            .build();
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "Classic similarity has been removed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_the_literal_classic_literal_similarity_has_been_removed",
            "Fields which use classic similarity: [[type: _doc, field: classic_sim_field]]");
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(index));
        assertEquals(singletonList(expected), issues);
    }

    public void testClassicSimilaritySettingsCheck() {
        IndexMetaData index = IndexMetaData.builder(randomAlphaOfLengthBetween(5, 10))
            .settings(settings(
                VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, VersionUtils.getPreviousVersion(Version.CURRENT)))
                .put("index.similarity.my_classic_similarity.type", "classic")
                .put("index.similarity.my_okay_similarity.type", "BM25"))
            .numberOfShards(randomIntBetween(1, 100))
            .numberOfReplicas(randomIntBetween(1, 100))
            .build();

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "Classic similarity has been removed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_the_literal_classic_literal_similarity_has_been_removed",
            "Custom similarities defined using classic similarity: [my_classic_similarity]");
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(index));
        assertEquals(singletonList(expected), issues);
    }

    public void testNodeLeftDelayedTimeCheck() {
        String negativeTimeValue = "-" + randomPositiveTimeValue();
        String indexName = randomAlphaOfLengthBetween(0, 10);
        String setting = UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING.getKey();

        final IndexMetaData badIndex = IndexMetaData.builder(indexName)
            .settings(settings(Version.CURRENT).put(setting, negativeTimeValue))
            .numberOfShards(randomIntBetween(1, 100))
            .numberOfReplicas(randomIntBetween(1, 15))
            .build();
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "Negative values for " + setting + " are deprecated and should be set to 0",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_literal_index_unassigned_node_left_delayed_timeout_literal_may_no_longer_be_negative",
            "The index [" + indexName + "] has [" + setting + "] set to [" + negativeTimeValue +
                "], but negative values are not allowed");

        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(badIndex));
        assertEquals(singletonList(expected), issues);

        final IndexMetaData goodIndex = IndexMetaData.builder(indexName)
            .settings(settings(Version.CURRENT))
            .numberOfShards(randomIntBetween(1, 100))
            .numberOfReplicas(randomIntBetween(1, 15))
            .build();
        List<DeprecationIssue> noIssues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(goodIndex));
        assertTrue(noIssues.isEmpty());
    }

    public void testShardOnStartupCheck() {
        String indexName = randomAlphaOfLengthBetween(0, 10);
        String setting = IndexSettings.INDEX_CHECK_ON_STARTUP.getKey();
        final IndexMetaData badIndex = IndexMetaData.builder(indexName)
            .settings(settings(Version.CURRENT).put(setting, "fix"))
            .numberOfShards(randomIntBetween(1, 100))
            .numberOfReplicas(randomIntBetween(1, 15))
            .build();
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "The value [fix] for setting [" + setting + "] is no longer valid",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_literal_fix_literal_value_for_literal_index_shard_check_on_startup_literal_is_removed",
            "The index [" + indexName + "] has the setting [" + setting + "] set to value [fix]" +
                ", but [fix] is no longer a valid value. Valid values are true, false, and checksum");
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(badIndex));
        assertEquals(singletonList(expected), issues);
        final IndexMetaData goodIndex = IndexMetaData.builder(indexName)
            .settings(settings(Version.CURRENT))
            .numberOfShards(randomIntBetween(1, 100))
            .numberOfReplicas(randomIntBetween(1, 15))
            .build();
        List<DeprecationIssue> noIssues = DeprecationChecks.filterChecks(INDEX_SETTINGS_CHECKS, c -> c.apply(goodIndex));
        assertTrue(noIssues.isEmpty());
    }
}
