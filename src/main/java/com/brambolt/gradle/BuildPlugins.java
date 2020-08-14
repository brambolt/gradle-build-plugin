package com.brambolt.gradle;

import org.gradle.api.Project;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.brambolt.gradle.BuildPlugins.Property.BRAMBOLT_RELEASE;
import static com.brambolt.gradle.BuildPlugins.Property.BUILD_DATE;
import static com.brambolt.gradle.BuildPlugins.Property.BUILD_NUMBER;
import static com.brambolt.gradle.BuildPlugins.Value.SNAPSHOT;
import static com.brambolt.gradle.BuildPlugins.Value.UNKNOWN;

public class BuildPlugins {

    public enum Property {
        BRAMBOLT_RELEASE("bramboltRelease"),
        BRAMBOLT_VERSION("bramboltVersion"),
        BUILD_DATE("buildDate"),
        BUILD_NUMBER("buildNumber");
        public final String name;
        Property(String name) {
            this.name = name;
        }
        public String toString() {
            return this.name;
        }
    }

    public enum Value {
        SNAPSHOT("SNAPSHOT"),
        UNKNOWN("UNKNOWN");
        public final String value;
        Value(String value) {
            this.value = value;
        }
        public String toString() {
            return value;
        }
    }

    public static String getBramboltRelease(Project project) {
        Object value = project.findProperty(BRAMBOLT_RELEASE.name);
        return (null != value) ? value.toString().trim() : UNKNOWN.value;
    }

    public static String getBramboltVersion(Project project) {
        Object value = project.findProperty(BUILD_NUMBER.name);
        if (null == value)
            return SNAPSHOT.value;
        String trimmed = value.toString().trim();
        return !SNAPSHOT.value.equals(trimmed)
            ? (getBramboltRelease(project) + "-" + trimmed) : SNAPSHOT.value;
    }

    public static String getBuildDate(Project project) {
        Object value = project.findProperty(BUILD_DATE.name);
        return null != value
            ? value.toString().trim()
            : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static String getBuildNumber(Project project) {
        Object value = project.findProperty(BUILD_NUMBER.name);
        if (null == value)
            return SNAPSHOT.value;
        String trimmed = value.toString().trim();
        return !trimmed.isEmpty() ? trimmed : SNAPSHOT.value;
    }
}
