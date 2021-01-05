/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.deprecation;

import org.elasticsearch.Build;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.PluginsAndModules;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.monitor.fs.FsInfo;
import org.elasticsearch.monitor.os.OsInfo;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.xpack.core.deprecation.DeprecationIssue;
import org.junit.Before;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING;
import static org.elasticsearch.discovery.DiscoveryModule.DISCOVERY_HOSTS_PROVIDER_SETTING;
import static org.elasticsearch.discovery.DiscoveryModule.DISCOVERY_TYPE_SETTING;
import static org.elasticsearch.node.Node.NODE_NAME_SETTING;
import static org.elasticsearch.xpack.deprecation.DeprecationChecks.NODE_SETTINGS_CHECKS;

public class NodeDeprecationChecksTests extends ESTestCase {
    private DiscoveryNode discoveryNode;
    private FsInfo.Path[] paths;
    private OsInfo osInfo;
    private PluginsAndModules pluginsAndModules;

    @Before
    public void setupDefaults() {
        discoveryNode = DiscoveryNode.createLocal(Settings.builder().put("node.name", "node_check").build(),
            new TransportAddress(TransportAddress.META_ADDRESS, 9200), "test");
        paths = new FsInfo.Path[] {};
        osInfo = new OsInfo(0L, 1, 1, randomAlphaOfLength(10),
            "foo-64", randomAlphaOfLength(10), randomAlphaOfLength(10));
        pluginsAndModules = new PluginsAndModules(Collections.emptyList(), Collections.emptyList());
    }

    private void assertSettingsAndIssue(String key, String value, DeprecationIssue expected) {
        Settings settings = Settings.builder()
            .put(CLUSTER_NAME_SETTING.getKey(), "elasticsearch")
            .put(NODE_NAME_SETTING.getKey(), "node_check")
            .put(DISCOVERY_TYPE_SETTING.getKey(), "single-node") // Needed due to NodeDeprecationChecks#discoveryConfigurationCheck
            .put(key, value)
            .build();
        List<NodeInfo> nodeInfos = Collections.singletonList(new NodeInfo(Version.CURRENT, Build.CURRENT,
            discoveryNode, settings, osInfo, null, null,
            null, null, null, pluginsAndModules, null, null));
        List<NodeStats> nodeStats = Collections.singletonList(new NodeStats(discoveryNode, 0L, null,
            null, null, null, null, new FsInfo(0L, null, paths), null, null, null,
            null, null, null, null));
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(NODE_SETTINGS_CHECKS, c -> c.apply(nodeInfos, nodeStats));
        assertEquals(singletonList(expected), issues);
    }

    public void testHttpEnabledCheck() {
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "HTTP Enabled setting removed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#remove-http-enabled",
            "nodes with http.enabled set: [node_check]");
        assertSettingsAndIssue("http.enabled", Boolean.toString(randomBoolean()), expected);
    }

    public void testAuditLoggingPrefixSettingsCheck() {
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "Audit log node info settings renamed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#audit-logfile-local-node-info",
            "nodes with audit log settings that have been renamed: [node_check]");
        assertSettingsAndIssue("xpack.security.audit.logfile.prefix.emit_node_host_address", Boolean.toString(randomBoolean()), expected);
        assertSettingsAndIssue("xpack.security.audit.logfile.prefix.emit_node_host_name", Boolean.toString(randomBoolean()), expected);
        assertSettingsAndIssue("xpack.security.audit.logfile.prefix.emit_node_name", Boolean.toString(randomBoolean()), expected);
    }

    public void testIndexThreadPoolCheck() {
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "Index thread pool removed in favor of combined write thread pool",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_index_thread_pool",
            "nodes with index thread pool settings: [node_check]");
        assertSettingsAndIssue("thread_pool.index.size", Integer.toString(randomIntBetween(1, 20000)), expected);
        assertSettingsAndIssue("thread_pool.index.queue_size", Integer.toString(randomIntBetween(1, 20000)), expected);
    }

    public void testBulkThreadPoolCheck() {
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "Bulk thread pool renamed to write thread pool",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#write-thread-pool-fallback",
            "nodes with bulk thread pool settings: [node_check]");
        assertSettingsAndIssue("thread_pool.bulk.size", Integer.toString(randomIntBetween(1, 20000)), expected);
        assertSettingsAndIssue("thread_pool.bulk.queue_size", Integer.toString(randomIntBetween(1, 20000)), expected);
    }

    public void testTribeNodeCheck() {
        String tribeSetting = "tribe." + randomAlphaOfLengthBetween(1, 20) + ".cluster.name";
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "Tribe Node removed in favor of Cross Cluster Search",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_tribe_node_removed",
            "nodes with tribe node settings: [node_check]");
        assertSettingsAndIssue(tribeSetting, randomAlphaOfLength(5), expected);
    }

    public void testHttpPipeliningCheck() {
        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
            "HTTP pipelining setting removed as pipelining is now mandatory",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#remove-http-pipelining-setting",
            "nodes with http.pipelining set: [node_check]");
        assertSettingsAndIssue("http.pipelining", Boolean.toString(randomBoolean()), expected);
    }

    public void testDiscoveryConfigurationCheck() {
        List<NodeStats> nodeStats = Collections.singletonList(new NodeStats(discoveryNode, 0L, null,
            null, null, null, null, new FsInfo(0L, null, paths), null, null, null,
            null, null, null, null));
        Settings baseSettings = Settings.builder()
            .put(CLUSTER_NAME_SETTING.getKey(), "elasticsearch")
            .put(NODE_NAME_SETTING.getKey(), "node_check")
            .build();

        {
            Settings hostsProviderSettings = Settings.builder().put(baseSettings)
                .put(DISCOVERY_HOSTS_PROVIDER_SETTING.getKey(), "file")
                .build();
            List<NodeInfo> nodeInfos = Collections.singletonList(new NodeInfo(Version.CURRENT, Build.CURRENT,
                discoveryNode, hostsProviderSettings, osInfo, null, null,
                null, null, null, pluginsAndModules, null, null));

            List<DeprecationIssue> issues = DeprecationChecks.filterChecks(NODE_SETTINGS_CHECKS, c -> c.apply(nodeInfos, nodeStats));
            assertTrue(issues.isEmpty());
        }

        {
            Settings hostsProviderSettings = Settings.builder().put(baseSettings)
                .put("discovery.zen.ping.unicast.hosts", "[1.2.3.4, 4.5.6.7]")
                .build();
            List<NodeInfo> nodeInfos = Collections.singletonList(new NodeInfo(Version.CURRENT, Build.CURRENT,
                discoveryNode, hostsProviderSettings, osInfo, null, null,
                null, null, null, pluginsAndModules, null, null));

            List<DeprecationIssue> issues = DeprecationChecks.filterChecks(NODE_SETTINGS_CHECKS, c -> c.apply(nodeInfos, nodeStats));
            assertTrue(issues.isEmpty());
        }

        {
            Settings hostsProviderSettings = Settings.builder().put(baseSettings)
                .build();
            List<NodeInfo> nodeInfos = Collections.singletonList(new NodeInfo(Version.CURRENT, Build.CURRENT,
                discoveryNode, hostsProviderSettings, osInfo, null, null,
                null, null, null, pluginsAndModules, null, null));

            DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.CRITICAL,
                "Discovery configuration is required in production mode",
                "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                    "#_discovery_configuration_is_required_in_production",
                "nodes which do not have discovery configured: [node_check]");
            List<DeprecationIssue> issues = DeprecationChecks.filterChecks(NODE_SETTINGS_CHECKS, c -> c.apply(nodeInfos, nodeStats));
            assertEquals(singletonList(expected), issues);
        }

    }

    public void testAzurePluginCheck() {
        Version esVersion = VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, Version.CURRENT);
        PluginInfo deprecatedPlugin = new PluginInfo(
            "repository-azure", "dummy plugin description", "dummy_plugin_version", esVersion,
            "javaVersion", "DummyPluginName", Collections.emptyList(), false);
        pluginsAndModules = new PluginsAndModules(Collections.singletonList(deprecatedPlugin), Collections.emptyList());

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "Azure Repository settings changed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_azure_repository_plugin",
            "nodes with repository-azure installed: [node_check]");
        assertSettingsAndIssue("foo", "bar", expected);
    }

    public void testGCSPluginCheck() {
        Version esVersion = VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, Version.CURRENT);
        PluginInfo deprecatedPlugin = new PluginInfo(
            "repository-gcs", "dummy plugin description", "dummy_plugin_version", esVersion,
            "javaVersion", "DummyPluginName", Collections.emptyList(), false);
        pluginsAndModules = new PluginsAndModules(Collections.singletonList(deprecatedPlugin), Collections.emptyList());

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "GCS Repository settings changed",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_google_cloud_storage_repository_plugin",
            "nodes with repository-gcs installed: [node_check]");
        assertSettingsAndIssue("foo", "bar", expected);
    }

    public void testFileDiscoveryPluginCheck() {
        Version esVersion = VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, Version.CURRENT);
        PluginInfo deprecatedPlugin = new PluginInfo(
            "discovery-file", "dummy plugin description", "dummy_plugin_version", esVersion,
            "javaVersion", "DummyPluginName", Collections.emptyList(), false);
        pluginsAndModules = new PluginsAndModules(Collections.singletonList(deprecatedPlugin), Collections.emptyList());

        DeprecationIssue expected = new DeprecationIssue(DeprecationIssue.Level.WARNING,
            "File-based discovery is no longer a plugin and uses a different path",
            "https://www.elastic.co/guide/en/elasticsearch/reference/master/breaking-changes-7.0.html" +
                "#_file_based_discovery_plugin",
            "nodes with discovery-file installed: [node_check]");
        assertSettingsAndIssue("foo", "bar", expected);
    }
}
