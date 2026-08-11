package promsafe4s

import cats.effect.kernel.Sync
import io.prometheus.metrics.core.metrics.{Histogram => JavaHistogram}
import io.prometheus.metrics.model.registry.PrometheusRegistry

final case class HistogramBuilder[F[_], A] private[promsafe4s] (
    name: String,
    help: String,
    encoder: LabelEncoder[A],
    customize: JavaHistogram.Builder => JavaHistogram.Builder
)(implicit F: Sync[F]) {
  def labels[B](next: LabelEncoder[B]): HistogramBuilder[F, B] = copy(encoder = next)
  def customizeWith(f: JavaHistogram.Builder => JavaHistogram.Builder): HistogramBuilder[F, A] =
    copy(customize = customize.andThen(f))
  def register(registry: PrometheusRegistry): F[Histogram[F, A]] = F.delay {
    val builder = customize(JavaHistogram.builder().name(name).help(help))
    builder.labelNames(encoder.names.toArray: _*)
    new Histogram[F, A](builder.register(registry), encoder)
  }
}

object Histogram {
  def builder[F[_]](name: String, help: String)(implicit F: Sync[F]): HistogramBuilder[F, Unit] =
    HistogramBuilder(name, help, LabelEncoder.empty[Unit], builder => builder)
}

final class Histogram[F[_], A] private[promsafe4s] (
    private val metric: JavaHistogram,
    private val encoder: LabelEncoder[A]
)(implicit F: Sync[F]) {
  lazy val unsafe: UnsafeHistogram[A] = new UnsafeHistogram[A](metric, encoder)

  def observe(labels: A, value: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).observe(value))

  def observe(value: Double)(implicit ev: A =:= Unit): F[Unit] = observe(ev.flip(()), value)

  def bind(labels: A): F[BoundHistogram[F]] = F.delay {
    val point = metric.labelValues(MetricSupport.values(encoder, labels): _*)
    new BoundHistogram[F](value => point.observe(value))
  }
}

final class UnsafeHistogram[A] private[promsafe4s] (
    private val metric: JavaHistogram,
    private val encoder: LabelEncoder[A]
) {

  /** Executes immediately and may throw a Prometheus client exception. */
  def observe(labels: A, value: Double): Unit =
    metric.labelValues(MetricSupport.values(encoder, labels): _*).observe(value)

  def observe(value: Double)(implicit ev: A =:= Unit): Unit = observe(ev.flip(()), value)
}

final class BoundHistogram[F[_]] private[promsafe4s] (observe0: Double => Unit)(implicit F: Sync[F]) {
  def observe(value: Double): F[Unit] = F.delay(observe0(value))
}
