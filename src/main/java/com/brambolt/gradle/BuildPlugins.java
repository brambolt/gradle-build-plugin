package com.brambolt.gradle;

import org.gradle.api.Project;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.brambolt.gradle.BuildProperty.BRAMBOLT_RELEASE;
import static com.brambolt.gradle.BuildProperty.BRAMBOLT_VERSION;
import static com.brambolt.gradle.BuildProperty.BUILD_DATE;
import static com.brambolt.gradle.BuildProperty.BUILD_NUMBER;
import static com.brambolt.gradle.BuildPropertyValue.SNAPSHOT;
import static com.brambolt.gradle.BuildPropertyValue.UNKNOWN;

public class BuildPlugins {

    /**
     * Accessor for the Brambolt release identifier.
     * @param project The project being built
     * @return The Brambolt release identifier
     */
    public static String getBramboltRelease(Project project) {
        Object value = project.findProperty(BRAMBOLT_RELEASE.name);
        return (null != value) ? value.toString().trim() : UNKNOWN.value;
    }

    /**
     * Accessor for the Brambolt version identifier.
     * @param project The project being built
     * @return The Brambolt version identifier
     */
    public static String getBramboltVersion(Project project) {
        // We do not want to use the version constant provided to the build,
        // so these lines are commented out:
        // Object existing = project.findProperty(BRAMBOLT_VERSION.name);
        // if (null != existing && !existing.toString().trim().isEmpty())
        //     return existing.toString();
        // Intentionally redefine the version constant here:
        Object value = project.findProperty(BUILD_NUMBER.name);
        if (null == value)
            return SNAPSHOT.value;
        String trimmed = value.toString().trim();
        return !SNAPSHOT.value.equals(trimmed)
            ? (getBramboltRelease(project) + "-" + trimmed) : SNAPSHOT.value;
    }

    /**
     * Accessor for the build timestamp.
     * @param project The project being built
     * @return The build timestamp
     */
    public static String getBuildDate(Project project) {
        Object value = project.findProperty(BUILD_DATE.name);
        return null != value
            ? value.toString().trim()
            : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    /**
     * Accessor for the build number.
     * @param project The project being built
     * @return The build number
     */
    public static String getBuildNumber(Project project) {
        Object value = project.findProperty(BUILD_NUMBER.name);
        if (null == value)
            return SNAPSHOT.value;
        String trimmed = value.toString().trim();
        return !trimmed.isEmpty() ? trimmed : SNAPSHOT.value;
    }
}
