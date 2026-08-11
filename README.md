# promsafe4s

`promsafe4s` provides typed, Cats Effect wrappers around the Prometheus Java
client. A metric carries a `LabelEncoder[A]`, so a labelled metric can only be
updated with the complete label value `A` instead of an uncounted Java varargs
list.

## Installation

The core module is cross-published for Scala 2.13 and Scala 3:

```scala
libraryDependencies +=
  "io.github.javierarrieta" %% "promsafe4s" % "0.1.0"
```

Optional semiautomatic derivation is published separately:

```scala
libraryDependencies +=
  "io.github.javierarrieta" %% "promsafe4s-derivation" % "0.1.0"
```

The API uses Cats Effect 3 and Prometheus Metrics Core 1.x. The examples below
use `IO`, but any effect with a Cats Effect `Sync` instance can be used.

## The basic idea

Define a label record and an encoder for it:

```scala
import promsafe4s._

final case class RequestLabels(method: String, status: Int)

val requestLabels =
  LabelEncoder[RequestLabels]
    .label("method")(_.method)
    .label("status")(_.status)
```

The encoder keeps label names and value extractors together. The order above is
the order passed to Prometheus. Each extracted value needs a Cats `Show`
instance; standard values such as `String`, `Int`, and `Boolean` already have
instances.

## Registering and updating a metric

Registration and mutation are effects. Pass a registry explicitly instead of
using hidden global state:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.prometheus.metrics.model.registry.PrometheusRegistry
import promsafe4s._

final case class RequestLabels(method: String, status: Int)

val requestLabels =
  LabelEncoder[RequestLabels]
    .label("method")(_.method)
    .label("status")(_.status)

val registry = new PrometheusRegistry()

val program: IO[Unit] = for {
  requests <- Counter
    .builder[IO]("http_requests_total", "Completed HTTP requests")
    .labels(requestLabels)
    .register(registry)
  _ <- requests.inc(RequestLabels("GET", 200))
  _ <- requests.incBy(RequestLabels("POST", 201), 2.0)
} yield ()

program.unsafeRunSync()
```

## Releasing

Publishing is configured through the GitHub Actions release workflow. Create a
GitHub Release whose tag matches the version to publish, such as `v0.1.0`.
The repository must have these encrypted secrets configured:

- `SONATYPE_USERNAME` and `SONATYPE_PASSWORD`: Sonatype Central Portal user token credentials.
- `PGP_SECRET`: the ASCII-armored private signing key.
- `PGP_PASSPHRASE`: the signing key passphrase, if the key is protected.

The following does not compile because `Counter` is not a Java-style varargs
API:

```scala
requests.inc("GET", "200")
```

The compiler requires a `RequestLabels` value instead.

## Unlabelled metrics

Builders start with an empty `Unit` label schema. Unlabelled metrics have
convenience overloads that do not require passing `()`:

```scala
val registry = new PrometheusRegistry()

val program = for {
  active <- Gauge
    .builder[IO]("active_connections", "Current active connections")
    .register(registry)
  _ <- active.set(42.0)
  _ <- active.inc
  _ <- active.dec
} yield ()
```

## Metric types

The core module wraps the four stateful metric families below.

### Counter

Counters support `inc`, `incBy`, and `bind`:

```scala
val program = for {
  counter <- Counter
    .builder[IO]("jobs_completed_total", "Completed jobs")
    .labels(requestLabels)
    .register(registry)
  _ <- counter.inc(RequestLabels("worker-a", 200))
  point <- counter.bind(RequestLabels("worker-a", 200))
  _ <- point.inc
  _ <- point.incBy(3.0)
} yield ()
```

### Gauge

Gauges support setting, incrementing, and decrementing values:

```scala
val program = for {
  gauge <- Gauge
    .builder[IO]("queue_depth", "Current queue depth")
    .labels(requestLabels)
    .register(registry)
  _ <- gauge.set(RequestLabels("worker-a", 200), 12.0)
  _ <- gauge.inc(RequestLabels("worker-a", 200))
  point <- gauge.bind(RequestLabels("worker-a", 200))
  _ <- point.decBy(2.0)
} yield ()
```

### Histogram and Summary

Both provide `observe` and `bind`:

```scala
val program = for {
  latency <- Histogram
    .builder[IO]("request_duration_seconds", "Request duration")
    .labels(requestLabels)
    .register(registry)
  sizes <- Summary
    .builder[IO]("request_size_bytes", "Request size")
    .labels(requestLabels)
    .register(registry)
  labels = RequestLabels("GET", 200)
  _ <- latency.observe(labels, 0.125)
  latencyPoint <- latency.bind(labels)
  _ <- latencyPoint.observe(0.250)
  _ <- sizes.observe(labels, 512.0)
} yield ()
```

Metric-specific Java builder options can be supplied with `customizeWith`.
The function runs before promsafe4s applies the encoder’s label names, so it
cannot replace the typed label schema:

```scala
val histogram = Histogram
  .builder[IO]("request_duration_seconds", "Request duration")
  .customizeWith(builder => builder) // configure the upstream Java builder here
  .labels(requestLabels)
```

Use the Prometheus Java client documentation for the options available on each
upstream builder, such as histogram buckets and summary quantiles.

## Semiautomatic derivation

The optional derivation module can create an encoder from a dedicated case
class:

```scala
import promsafe4s.derivation.semiauto

final case class RequestLabels(method: String, status: Int)

val requestLabels = semiauto.deriveLabelEncoder[RequestLabels]
```

The derived encoder uses the case-class field names and includes every field.
It is best suited to a small record whose fields are intentionally all
Prometheus labels.

Scala 3 uses Magnolia-based derivation. Scala 2.13 uses JVM case-class product
metadata. For a stable schema, custom `Show` rendering, renamed labels, or
omitted fields, use an explicit `LabelEncoder` in both Scala versions.

Derivation is semiautomatic: importing the module does not cause every case
class in an application to acquire an encoder implicitly.

## Custom value types

Use `Show` when a domain value needs a specific label representation:

```scala
import cats.Show

final case class Status(code: Int)

implicit val statusShow: Show[Status] =
  Show.show(status => s"http-${status.code}")

final case class Labels(status: Status)

val labels =
  LabelEncoder[Labels]
    .label("status")(_.status)
```

This makes label formatting explicit and avoids relying on `toString` in the
core API.

## Performance and cardinality

Prometheus creates a time series for each unique combination of label values.
Do not use high-cardinality values such as request IDs, timestamps, URLs, or
user IDs as labels.

When a label combination is reused in a hot path, call `bind` once and retain
the returned bound data point. This avoids repeating the Java client’s label
lookup on every update.

## Errors and effects

Builder configuration, registration, label lookup, and metric mutation are
suspended in `F`. Invalid metric names, duplicate registration, and other
Prometheus client failures are raised through the effect with the original
Java exception and message.

No registry is created or mutated until the returned effect is executed.

## Imperative updates

For integrations whose callback API cannot return an effect, `Counter` and
`Counter`, `Gauge`, and `Histogram` provide separate `unsafe` views with the
same update operations as their effectful APIs. For example,
`counter.unsafe.inc(labels)` updates the Prometheus client immediately and may
throw client exceptions. The views preserve the typed label contract but are
not referentially transparent; prefer the effectful methods whenever the caller
can compose `F`.

## Scope and compatibility

The current release targets:

- Scala 2.13 and Scala 3.
- Java 11 bytecode/runtime baseline.
- Prometheus Metrics Core 1.x.
- Counter, Gauge, Histogram, and Summary.

Callback metrics, Info, StateSet, exemplars, automatic timer resources,
Scala.js, and Scala Native are not currently included.
