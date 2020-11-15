package com.brambolt.gradle;

/**
 * Build plugin property name definitions.
 */
public enum BuildProperty {

    ARTIFACT_ID("artifactId"),

    /**
     * The primary release identifier, often
     * chronological (<code>2020.02.02</code>) or
     * semantic (<code>2.2.2</code>).
     */
    BRAMBOLT_RELEASE("bramboltRelease"),

    /**
     * The full version number, usually formed by combining the release
     * and the build number, normally similar to <code>2020.02.02-12345</code>.
     */
    BRAMBOLT_VERSION("bramboltVersion"),

    /**
     * The build timestamp.
     */
    BUILD_DATE("buildDate"),

    /**
     * The build number, normally similar to <code>12345</code>.
     */
    BUILD_NUMBER("buildNumber"),

    DEVELOPERS("developers"),

    INCEPTION_YEAR("inceptionYear"),

    LICENSES("licenses"),

    PLUGIN_CLASS("pluginClass"),

    PLUGIN_DISPLAY_NAME("pluginDisplayName"),

    PLUGIN_ID("pluginId"),

    PLUGIN_TAGS("pluginTags"),

    PLUGIN_WEBSITE("pluginWebsite"),

    RELEASE("release"),

    VCS_URL("vcsUrl");

    public final String name;

    BuildProperty() {
        this(null);
    }

    BuildProperty(String name) {
        this.name = null != name && !name.trim().isEmpty() ? name : name();
    }

    public String toString() {
        return name;
    }
}
