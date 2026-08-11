package promsafe4s

import scala.jdk.CollectionConverters._

import cats.Show
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.{CounterSnapshot, GaugeSnapshot, HistogramSnapshot}
import munit.FunSuite

final class LabelEncoderSuite extends FunSuite {
  final case class Labels(method: String, status: Int)

  test("explicit encoders preserve declared names and order") {
    val encoder = LabelEncoder[Labels]
      .label("method")(_.method)
      .label("status")(_.status)

    assertEquals(encoder.names, Vector("method", "status"))
    assertEquals(encoder.values(Labels("GET", 200)), Vector("GET", "200"))
  }

  test("contramap changes the input type without changing the schema") {
    final case class Request(labels: Labels)
    val encoder = LabelEncoder[Labels]
      .label("method")(_.method)
      .contramap[Request](_.labels)

    assertEquals(encoder.values(Request(Labels("POST", 201))), Vector("POST"))
  }

  test("custom Show instances control label rendering") {
    final case class Status(value: Int)
    implicit val statusShow: Show[Status] = Show.show(status => s"status-${status.value}")
    val encoder = LabelEncoder[Status].label("status")(identity)

    assertEquals(encoder.values(Status(3)), Vector("status-3"))
  }

  test("a labelled gauge requires and updates a typed label record") {
    val registry = new PrometheusRegistry()
    val encoder = LabelEncoder[Labels]
      .label("method")(_.method)
      .label("status")(_.status)
    val result = for {
      gauge <- Gauge
        .builder[IO]("requests_in_flight", "Requests in flight")
        .labels(encoder)
        .register(registry)
      _ <- gauge.set(Labels("GET", 200), 1.0)
      _ <- gauge.bind(Labels("GET", 200)).flatMap(_.inc)
    } yield ()

    result.unsafeRunSync()
  }

  test("unsafe metric views update immediately") {
    val registry = new PrometheusRegistry()
    val labels = Labels("GET", 200)
    val metrics = (for {
      counter <- Counter
        .builder[IO]("unsafe_requests", "Requests updated from an imperative callback")
        .labels(LabelEncoder[Labels].label("method")(_.method).label("status")(_.status))
        .register(registry)
      histogram <- Histogram
        .builder[IO]("unsafe_request_duration_seconds", "Durations updated from an imperative callback")
        .register(registry)
      gauge <- Gauge
        .builder[IO]("unsafe_active_requests", "Active requests updated from an imperative callback")
        .labels(LabelEncoder[Labels].label("method")(_.method).label("status")(_.status))
        .register(registry)
    } yield (counter, histogram, gauge)).unsafeRunSync()

    metrics._1.unsafe.inc(labels)
    metrics._2.unsafe.observe(0.25d)
    metrics._3.unsafe.set(labels, 2.0d)
    metrics._3.unsafe.incBy(labels, 3.0d)
    metrics._3.unsafe.decBy(labels, 1.0d)

    val snapshots = registry.scrape().iterator.asScala.toList
    val count = snapshots
      .find(_.getMetadata.getName == "unsafe_requests")
      .flatMap(_.getDataPoints.asScala.collectFirst {
        case counter: CounterSnapshot.CounterDataPointSnapshot => counter.getValue
      })
    val observations = snapshots
      .find(_.getMetadata.getName == "unsafe_request_duration_seconds")
      .flatMap(
        _.getDataPoints.asScala.collectFirst { case histogram: HistogramSnapshot.HistogramDataPointSnapshot =>
          histogram.getCount
        }
      )
    val gaugeValue = snapshots
      .find(_.getMetadata.getName == "unsafe_active_requests")
      .flatMap(_.getDataPoints.asScala.collectFirst { case gauge: GaugeSnapshot.GaugeDataPointSnapshot =>
        gauge.getValue
      })

    assertEquals(count, Some(1d))
    assertEquals(observations, Some(1L))
    assertEquals(gaugeValue, Some(4d))
  }
}
