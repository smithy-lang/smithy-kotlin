# Tracing Design

* **Type**: Design
* **Author(s)**: Ian Botsford

# Abstract

Tracing describes the emission of logging and metric events in a structured manner for the purposes of analyzing SDK
performance and debugging. This document presents a design for how tracing will work in the SDK.

# Concepts

The following terms are defined:

* **Trace span**: A logical grouping of tracing events that encompasses some operation. Trace spans may be subdivided
  into child spans which group a narrower set of events within the context of the parent. Trace spans are hierarchical;
  events that occur within one span also logically occur within the ancestors of that span.

* **Trace probe**: A receiver for tracing events. A probe is notified when new events occur within a span and may take
  appropriate action to route the event (e.g., forward to a downstream logging/metrics framework, print to the console,
  write to a file, etc.).

# Design

The following components provide tracing support:

## Tracer

A `Tracer` is a top-level provider of tracing capabilities. It bridges trace spans (into which events are emitted) and
trace probes (which receive events and handle them accordingly). Typically, each service client will have its own
internal `Tracer` instance. That `Tracer` need not be publicly accessible but must be configurable with a trace probe
and client name at service client construction.

The `Tracer` interface is specified as:

```kotlin
interface Tracer {
    fun createRootSpan(): TraceSpan
}
```

A `Tracer` provides the root span for a service client, into which all events over the lifetime of that client will be
emitted. Child spans can be created as mentioned below in the [Trace Span](#trace-span) section.

**Note**: The interface does not specify how trace probes will be configured or utilized. These are implementation
details of the tracer and aren't necessary in the public interface.

## Trace Span

A `TraceSpan` is a logical grouping of tracing events that are associated with some operation. Spans may be subdivided
into child spans which group a narrower set of events within the context of the parent. Spans are hierarchical; events
that occur within one span also logically occur within the ancestors of that span.

The `TraceSpan` interface is specified as:

```kotlin
interface TraceSpan : Closeable {
    val id: String
    val parent: TraceSpan?

    fun child(id: String): TraceSpan
    fun postEvents(events: Iterable<TraceEvent>)
}
```

Spans have an ID (or name) which must be unique among sibling spans within the same parent. Span IDs will generally be
used by probes to contextualize events.

`TraceSpan` instances are `Closeable` and must be closed when no more events will be emitted to them. Probes may choose
to batch/aggregate events within a span until a span is closed.

## Trace Event

A `TraceEvent` is the recording of a single event that took place and its associated metadata:

```kotlin
data class TraceEvent(
    val level: EventLevel,
    val sourceComponent: String,
    val timestamp: Instant,
    val threadId: String,
    val data: TraceEventData,
)

enum class EventLevel { Fatal, Error, Warning, Info, Debug, Trace }

sealed interface TraceEventData {
  data class Message(val exception: Throwable? = null, val content: () -> Any?) : TraceEventData

  sealed interface Metric : TraceEventData { val metric: String }
  data class Count<T : Number>(override val metric: String, val count: () -> T) : Metric
  data class Timespan(override val metric: String, val duration: () -> Duration) : Metric
}
```

Trace events occur at different levels (e.g., fatal, info, debug, etc.). These levels may be used by probes to
include/omit events in their output.

Trace events can be one of three types:
* `Message`: Typically, a free-form text message used for logging
* `Count`: The numerical measurement of some value (e.g., results returned, bytes written, etc.)
* `Timespan`: The temporal measurement of some occurrence (e.g., time elapsed, latency, etc.)

Probes are free to handle these different types of events however they see fit (e.g., they may log some messages,
aggregate some metrics, ignore some events, etc.).

Event data values (i.e., message text, count values, and timespan durations) are provided as lambdas rather than with
direct values. This allows probe implementations to skip calculating them in the event they would otherwise be discarded
(e.g., for events emitted at a level ignored by the probe).

## Trace Probe

A `TraceProbe` is a sink for receiving events from spans. They will typically form a bridge between the SDK's events and
downstream libraries/frameworks/services which can handle the events. Examples of such downstream systems include Log4j,
CloudWatch, local files on disk, the console, etc.

SDKs will typically not bundle many implementations of `TraceProbe` themselves. Common probe implementations may be
available as separate libraries or from third-party sources. Users may implement probes themselves to bridge SDK events
to whatever downstream systems they desire.

The `TraceProbe` interface is defined as:

```kotlin
interface TraceProbe {
    fun postEvents(span: TraceSpan, events: Iterable<TraceEvent>)
    fun spanClosed(span: TraceSpan)
}
```

The methods of `TraceProbe` are invoked by the top-level `Tracer` (or `TraceSpan` instances created by it).

The `postEvents` method indicates that events have been emitted to a span. Probe implementations may choose to
immediately handle/discard those events or to batch them until later. Once `spanClosed` is called, no more events will
be posted for the given span.

## Client config

The following additional parameters will be added to client config:

* `clientName`: An optional client name may be used by `Tracer` implementations to name the root trace span. If not
  provided explicitly, it defaults to the service name (e.g., "S3", "DynamoDB", etc.—the name of the client interface
  without the suffix "Client"). Callers that instantiate multiple clients for a single service (e.g., using different
  configuration or for different application purposes) may choose to provide differing client names which would yield
  trace events with different spans.
* `traceProbe`: An optional probe to receive trace events. If not provided explicitly, a no-op probe is used which
  discards all events.

# Revision history

* 8/19/2022 - Initial draft
