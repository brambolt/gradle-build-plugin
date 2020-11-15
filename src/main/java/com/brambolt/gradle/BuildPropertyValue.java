package com.brambolt.gradle;

/**
 * Build plugin property value definitions.
 */
public enum BuildPropertyValue {

    /**
     * The <code>SNAPSHOT</code> build number and/or version identifier.
     */
    SNAPSHOT,

    /**
     * The <code>UNKNOWN</code> release.
     */
    UNKNOWN;

    public final String value;

    BuildPropertyValue() {
        this(null);
    }

    BuildPropertyValue(String value) {
        this.value = null != value && !value.trim().isEmpty() ? value : name();
    }

    public String toString() {
        return value;
    }
}
