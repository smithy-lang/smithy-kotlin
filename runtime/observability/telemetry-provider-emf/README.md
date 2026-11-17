# EMF Telemetry Provider

A telemetry provider that emits SDK metrics in [CloudWatch Embedded Metric Format (EMF)](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Embedded_Metric_Format.html).

Metrics are written as structured JSON to stdout. Wherever something already forwards stdout to
CloudWatch Logs — the Lambda runtime, the ECS `awslogs` driver, or the CloudWatch agent — those logs
are ingested and CloudWatch extracts the metrics from them: no `PutMetricData` calls, no extra
credentials, and no publishing thread in your process. This makes EMF a good fit for AWS Lambda,
where a background metric-publishing thread cannot run reliably.

## Configuration

### Gradle

```kts
dependencies {
    implementation("aws.sdk.kotlin:s3:$SDK_VERSION") // and any other AWS SDK clients...
    implementation("aws.smithy.kotlin:telemetry-provider-emf:$SMITHY_KOTLIN_VERSION")
}
```

### AWS SDK for Kotlin

```kt
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.telemetry.emf.EmfTelemetryProvider

val client = S3Client {
    telemetryProvider = EmfTelemetryProvider {
        namespace = "MyApp"
    }
}
```

To apply it to every SDK client in your application, set it globally during startup instead:

```kt
import aws.smithy.kotlin.runtime.telemetry.GlobalTelemetryProvider

GlobalTelemetryProvider.set(EmfTelemetryProvider { namespace = "MyApp" })
```

This must be called exactly once, before any client is constructed — a second call throws.
`GlobalTelemetryProvider` lives in the `telemetry-defaults` module, which you will need on your
classpath.

### Options

| Option | Default | Notes |
|---|---|---|
| `namespace` | `AwsSdk/KotlinSdk` | The CloudWatch namespace metrics are published under. 1–1024 characters. |
| `logGroupName` | `AWS_LAMBDA_LOG_GROUP_NAME` | Included as the `LogGroupName` field. Omitted from the metrics JSON if null. See below. |
| `loggerProvider` | `LoggerProvider.None` | For the SDK's own logging — **not** EMF output. |

## Environment support

Whether metrics reach CloudWatch depends on how your environment handles stdout.

| Environment | Works out of the box? |
|---|---|
| AWS Lambda | Yes. Stdout goes to CloudWatch Logs, and `logGroupName` is picked up from `AWS_LAMBDA_LOG_GROUP_NAME`. |
| Amazon ECS / EKS with the `awslogs` driver | Yes, but set `logGroupName` explicitly — the Lambda variable isn't present. |
| Amazon EC2 or on-premises | Only with the [CloudWatch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html) configured to collect the stream. Set `logGroupName` explicitly. |
| Local development | Metrics print to your console. Nothing is sent to CloudWatch. |

`logGroupName` only labels the metrics JSON; it does not create or route to a log group. Where the
output lands is determined entirely by whatever collects your process's stdout.

## What gets emitted

One line of metrics JSON per recorded value. A single call duration looks like:

```json
{
  "_aws": {
    "Timestamp": 1753920000000,
    "LogGroupName": "/aws/lambda/my-fn",
    "CloudWatchMetrics": [
      {
        "Namespace": "MyApp",
        "Dimensions": [["rpc.service", "rpc.method"]],
        "Metrics": [{ "Name": "smithy.client.call.duration", "Unit": "Seconds" }]
      }
    ]
  },
  "rpc.service": "S3",
  "rpc.method": "GetObject",
  "smithy.client.call.duration": 0.042
}
```

Shown formatted for readability; the actual output is minified onto a single line, which is what
CloudWatch requires.

Metric attributes become CloudWatch dimensions, so every distinct combination of values creates a
separate metric series. Attributes with high cardinality will multiply the number of custom metrics
CloudWatch bills you for.

### Limitations

- **Async instruments are not collected.** Gauges and async up-down counters are sampled on an
  interval by their backend; EMF has no backend and no collection loop, so these instruments are
  registered but never emitted. This affects the HTTP client's connection-pool and request-concurrency
  gauges. Histograms and counters — including all call-level latency and retry metrics — are
  unaffected.
- **Tracing is not supported.** `tracerProvider` is `TracerProvider.None`; EMF has no span
  representation. Use the OpenTelemetry provider if you need traces.
- **Values that exceed EMF limits are corrected, not rejected.** Over-long metric names, dimension
  keys, and dimension values are truncated, and dimension sets beyond 30 entries are dropped, each
  with a warning on stdout. Emitting telemetry never fails the operation being measured.

## Troubleshooting

### Metrics appear in Logs but not in Metrics

Check that the log group actually receives the metrics JSON and that it is valid EMF — CloudWatch
silently ignores log lines it cannot parse. The most common cause is another logging layer capturing
stdout and wrapping or prefixing the output; each metrics JSON must be written on its own line,
unmodified.

### Nothing appears at all

Confirm your environment forwards stdout to CloudWatch Logs (see the table above). On EC2 and
on-premises hosts this requires the CloudWatch agent; without it, metrics are written to the console
and discarded.

### Dimensions are missing from a metric

A dimension set is capped at 30 entries, and anything beyond that is dropped with a `[WARN]` line on
stdout. If a metric has more attributes than that, reduce them at the call site rather than relying on
which ones survive.
