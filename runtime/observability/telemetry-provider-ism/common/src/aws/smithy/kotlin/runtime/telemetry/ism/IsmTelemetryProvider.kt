package aws.smithy.kotlin.runtime.telemetry.ism

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.telemetry.DefaultTelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import aws.smithy.kotlin.runtime.time.Clock

@ExperimentalApi
public class IsmTelemetryProvider internal constructor(
    sink: IsmMetricSink,
    override val loggerProvider: LoggerProvider = DefaultTelemetryProvider.loggerProvider,
    clock: Clock,
) : TelemetryProvider {
    public constructor(sink: IsmMetricSink, loggerProvider: LoggerProvider = DefaultTelemetryProvider.loggerProvider) :
        this(sink, loggerProvider, Clock.System)

    /**
     * Narrower type of [contextManager]
     */
    private val _contextManager = IsmContextManager(sink)

    override val contextManager: ContextManager by ::_contextManager

    override val meterProvider: MeterProvider = IsmMeterProvider(_contextManager.metricListener, clock)

    override val tracerProvider: TracerProvider = IsmTracerProvider(_contextManager.spanListener)
}

@ExperimentalApi
public interface IsmMetricSink {
    public fun onInvocationComplete(operationMetrics: OperationMetrics)
}
