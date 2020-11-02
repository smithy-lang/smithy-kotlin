package software.aws.clientrt.logging

/**
 * A platform independent marker to enrich log statements.
 */
public expect interface Marker {

    public fun getName(): String
}
