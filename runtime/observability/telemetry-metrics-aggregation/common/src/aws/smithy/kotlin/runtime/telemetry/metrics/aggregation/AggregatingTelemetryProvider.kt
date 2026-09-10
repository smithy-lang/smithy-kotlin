/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics.aggregation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.Logger
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider

/**
 * Base for [TelemetryProvider]s that aggregate measurements in process before emitting them.
 *
 * Holds every part that does not vary between aggregating providers — the instrument registry and its
 * accumulators, dimension keying, the cardinality guard, and the diagnostics logger — and leaves exactly
 * one thing to subclasses: **what triggers a collection and what happens to the result.**
 *
 * Two consumers are anticipated:
 *
 * - [SdkTelemetryProvider] — collects on an interval via a [MetricReader] and hands the batch to a
 *   [MetricExporter]. This is the CloudWatch path.
 * - A future EMF provider — collects at the end of each client call and writes one document, replacing
 *   `telemetry-provider-emf`'s current one-document-per-measurement behaviour. No timer, no reader.
 *
 * The granularity difference between those two is the *only* difference, which is the point: it is
 * expressible as an override of when [collect] runs.
 *
 * ### Why the aggregation state is composed, not inherited
 *
 * [SdkMeterProvider] is held as a field rather than being the superclass. Aggregation is reused *without
 * variation*, and composition is the right tool for that — subclassing it would invite a subclass to
 * override accumulator behaviour, which is precisely the part that must stay identical for the two
 * consumers' results to be comparable.
 *
 * ### Why this class is not part of the supported API
 *
 * An abstract class pins its protected surface: every `protected` member becomes something third-party
 * subclasses may depend on and that cannot change without a breaking release. `@InternalApi` keeps that
 * surface free to change — the class must be `public` only because [SdkTelemetryProvider], which is
 * supported, extends it, and Kotlin does not allow a public subclass of an internal class. Promoting it to
 * supported later is a compatible change; retracting it would not be. The extension point for callers and
 * for other backends is [MetricExporter].
 *
 * @param config dimension allowlist, aggregation fidelity, and the growth bounds.
 * @param loggerProvider where the pipeline's own diagnostics go.
 * @param loggerName names the logger so diagnostics are attributable to the concrete provider.
 */
@InternalApi
public abstract class AggregatingTelemetryProvider(
    config: AggregationConfig,
    final override val loggerProvider: LoggerProvider,
    loggerName: String,
) : TelemetryProvider,
    Closeable {
    /**
     * Diagnostics for the pipeline itself — cardinality overflow, export failures, dropped datums. Routed
     * through the caller's [LoggerProvider] so they land wherever the application's logs go.
     *
     * `protected` because subclasses must pass it to whatever drives collection; [loggerName] is a
     * constructor parameter so each provider's diagnostics are attributable to it rather than all appearing
     * under a shared base-class name.
     */
    protected val logger: Logger = loggerProvider.getOrCreateLogger(loggerName)

    /**
     * `protected` because a subclass that delegates collection to a [MetricReader] must hand the reader the
     * provider it collects from ([MetricReader.install]); one that emits directly can ignore this and use
     * [collect].
     */
    protected val aggregating: SdkMeterProvider = SdkMeterProvider(config, logger)

    /**
     * `final`: the whole contract of this class is that instruments accumulate the same way for every
     * subclass. A subclass substituting its own [MeterProvider] would inherit the lifecycle plumbing while
     * silently opting out of the aggregation, which is the one mistake this factoring exists to prevent.
     */
    final override val meterProvider: MeterProvider = aggregating

    // Open, not final, and both defaulted to None: this base is about metrics, so a subclass that also
    // implements tracing (an EMF provider plausibly would) must be able to supply a real one. Defaults are
    // None rather than abstract so a metrics-only subclass declares nothing.
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None

    /**
     * Snapshot and reset every accumulator.
     *
     * Delta temporality: each call returns what accumulated since the previous call, so calling it from two
     * places would split one interval across two batches. Subclasses must funnel all collection through a
     * single trigger.
     *
     * `protected`, not public: this is reset-on-read state, and a public collect on the provider would let
     * application code silently steal an interval from whatever is actually publishing.
     */
    protected fun collect(): List<MetricData> = aggregating.collect()

    /** Collect and emit immediately, whatever "emit" means for the subclass. */
    public abstract suspend fun flush()
}
