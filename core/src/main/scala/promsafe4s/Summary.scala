package promsafe4s

import cats.effect.kernel.Sync
import io.prometheus.metrics.core.metrics.{Summary => JavaSummary}
import io.prometheus.metrics.model.registry.PrometheusRegistry

final case class SummaryBuilder[F[_], A] private[promsafe4s] (
    name: String,
    help: String,
    encoder: LabelEncoder[A],
    customize: JavaSummary.Builder => JavaSummary.Builder
)(implicit F: Sync[F]) {
  def labels[B](next: LabelEncoder[B]): SummaryBuilder[F, B] = copy(encoder = next)
  def customizeWith(f: JavaSummary.Builder => JavaSummary.Builder): SummaryBuilder[F, A] =
    copy(customize = customize.andThen(f))
  def register(registry: PrometheusRegistry): F[Summary[F, A]] = F.delay {
    val builder = customize(JavaSummary.builder().name(name).help(help))
    builder.labelNames(encoder.names.toArray: _*)
    new Summary[F, A](builder.register(registry), encoder)
  }
}

object Summary {
  def builder[F[_]](name: String, help: String)(implicit F: Sync[F]): SummaryBuilder[F, Unit] =
    SummaryBuilder(name, help, LabelEncoder.empty[Unit], builder => builder)
}

final class Summary[F[_], A] private[promsafe4s] (
    private val metric: JavaSummary,
    private val encoder: LabelEncoder[A]
)(implicit F: Sync[F]) {
  def observe(labels: A, value: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).observe(value))

  def observe(value: Double)(implicit ev: A =:= Unit): F[Unit] = observe(ev.flip(()), value)

  def bind(labels: A): F[BoundSummary[F]] = F.delay {
    val point = metric.labelValues(MetricSupport.values(encoder, labels): _*)
    new BoundSummary[F](value => point.observe(value))
  }
}

final class BoundSummary[F[_]] private[promsafe4s] (observe0: Double => Unit)(implicit F: Sync[F]) {
  def observe(value: Double): F[Unit] = F.delay(observe0(value))
}
