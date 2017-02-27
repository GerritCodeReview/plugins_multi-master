load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
)

gerrit_plugin(
    name = "multi-master",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: multi-master",
        "Gerrit-Module: com.ericsson.gerrit.plugins.multimaster.Module",
        "Gerrit-HttpModule: com.ericsson.gerrit.plugins.multimaster.HttpModule",
        "Implementation-Title: multi-master plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/multi-master",
    ],
)

junit_tests(
    name = "multi_master_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["multi-master"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":multi-master__plugin",
        "@mockito//jar",
        "@wiremock//jar",
    ],
)
