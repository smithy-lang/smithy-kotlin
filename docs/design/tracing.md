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
    fun createRootSpan(id: String): TraceSpan
}
```

A `Tracer` provides root spans for a service client, into which all events over the lifetime of an operation will be
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
    fun postEvent(event: TraceEvent)
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
    fun postEvent(span: TraceSpan, event: TraceEvent)
    fun spanClosed(span: TraceSpan)
}
```

The methods of `TraceProbe` are invoked by the top-level `Tracer` (or `TraceSpan` instances created by it).

The `postEvent` method indicates that an event has been emitted to a span. Probe implementations may choose to
immediately handle/discard events or to batch them until later. Once `spanClosed` is called, no more events will be
posted for the given span.

## Client config

The following additional parameters will be added to client config:

* `tracer`: An optional `Tracer` implementation to use. If not provided explicitly, this will default to a tracer which
  sends logging events to **kotlin-logging** and ignores metric events. The `DefaultTracer` class is available to
  provide a simple `Tracer` implementation with a configurable probe and root prefix. Using a root prefix can help
  differentiate events from multiple clients of a single service used for different use cases.

# Implementation guidance

The following guidelines are intended to inform implementation and usage of tracing features by SDK contributors and
those who customize their usage of the SDK:

## Trace span hierarchy

Trace spans form a taxonomy that categorize tracing events into a hierarchy. Discrete spans help group related events in
a way that's useful to downstream tools which facilitate analysis. Consequently, choosing meaningful trace spans is key
to maximizing the usefulness of tracing events. Trace spans which are too specific and too deeply nested may create
noise and obscure events in an opaque hierarchy. Trace spans which are too shallow may bundle together too many events
and hinder meaningful analyses by downstream systems.

The following trace span levels are recommended for implementors:

* A top-level span for each operation invocation, in the form of `<clientName>-<operation>-<uuid>` (e.g.,
  `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac`)
* A span for retry attempts, in the form of `Attempt-<n>` (e.g., `"Attempt-1"`) or `Non-retryable attempt` in the case
  of operations which cannot be retried
* A span for credentials chains, named `Credentials chain`. Note that individual credentials providers (e.g., static,
  profile, environment, etc.) don't get their own child spans—only the chain.
* A span for HTTP engine events within a request, named `HTTP`
* Spans for subclients (or _inner clients_) which are embedded in the logic for superclients (or _outer clients_). An
  example of a subclient is using a nested STS client as part of credential resolution while invoking an operation for a
  different service. Spans for subclients effectively reproduce the span hierarchy listed above nested within the outer
  span hierarchy.

The following are examples of suggested trace span hierarchies:

* `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac`: events which occur during invocation of an S3 `ListBuckets`
  operation _before_ or _after_ retry middleware (e.g., serialization/deserialization, endpoint resolution, etc.)
* `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac/Attempt-1`: events which occur during the first attempt at
  calling `ListBuckets` _outside of_ the HTTP engine (e.g., signing)
* `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac/Attempt-1/Credentials chain`: events which occur during
  credential resolution in a credentials chain during the first attempt at calling `ListBuckets`
* `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac/Attempt-1/HTTP`: events which occur inside the HTTP engine during
  the first attempt at calling `ListBuckets` (e.g., sending/receiving bytes from service)

The following is an example of a nested span hierarchy for a subclient:

* `S3-ListBuckets-8e6bf409-c119-4661-bd99-523c70701aac/Attempt-1/Credentials chain/SSO-AssumeRole-c080b2e2-ff6d-4504-bce4-3433f9f4ac1b/Attempt-2`:
  events which occur during the second attempt to call SSO's `AssumeRole` as part of credential chain resolution during
  the first attempt to call S3's `ListBuckets`.

### Adding new spans

New spans may be necessary for certain features in the future and thus the above list and examples are not exhaustive.
For the reasons described above, care should be taken to ensure that new spans add enough value and distinctiveness
without nesting so deeply as to obscure event relationships.

# Revision history

* 8/19/2022 - Initial draft
* 11/15/2022 - Revised draft with latest proposed interfaces
