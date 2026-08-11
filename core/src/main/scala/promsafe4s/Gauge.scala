package promsafe4s

import cats.effect.kernel.Sync
import io.prometheus.metrics.core.metrics.{Gauge => JavaGauge}
import io.prometheus.metrics.model.registry.PrometheusRegistry

final case class GaugeBuilder[F[_], A] private[promsafe4s] (
    name: String,
    help: String,
    encoder: LabelEncoder[A],
    customize: JavaGauge.Builder => JavaGauge.Builder
)(implicit F: Sync[F]) {
  def labels[B](next: LabelEncoder[B]): GaugeBuilder[F, B] = copy(encoder = next)
  def customizeWith(f: JavaGauge.Builder => JavaGauge.Builder): GaugeBuilder[F, A] =
    copy(customize = customize.andThen(f))
  def register(registry: PrometheusRegistry): F[Gauge[F, A]] = F.delay {
    val builder = customize(JavaGauge.builder().name(name).help(help))
    builder.labelNames(encoder.names.toArray: _*)
    new Gauge[F, A](builder.register(registry), encoder)
  }
}

object Gauge {
  def builder[F[_]](name: String, help: String)(implicit F: Sync[F]): GaugeBuilder[F, Unit] =
    GaugeBuilder(name, help, LabelEncoder.empty[Unit], builder => builder)
}

final class Gauge[F[_], A] private[promsafe4s] (
    private val metric: JavaGauge,
    private val encoder: LabelEncoder[A]
)(implicit F: Sync[F]) {
  lazy val unsafe: UnsafeGauge[A] = new UnsafeGauge[A](metric, encoder)

  def set(labels: A, value: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).set(value))
  def inc(labels: A): F[Unit] = F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).inc())
  def incBy(labels: A, amount: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).inc(amount))
  def dec(labels: A): F[Unit] = F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).dec())
  def decBy(labels: A, amount: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).dec(amount))

  def set(value: Double)(implicit ev: A =:= Unit): F[Unit] = set(ev.flip(()), value)
  def inc(implicit ev: A =:= Unit): F[Unit] = inc(ev.flip(()))
  def dec(implicit ev: A =:= Unit): F[Unit] = dec(ev.flip(()))

  def bind(labels: A): F[BoundGauge[F]] = F.delay {
    val point = metric.labelValues(MetricSupport.values(encoder, labels): _*)
    new BoundGauge[F](
      () => point.get(),
      value => point.set(value),
      () => point.inc(),
      amount => point.inc(amount),
      () => point.dec(),
      amount => point.dec(amount)
    )
  }
}

final class UnsafeGauge[A] private[promsafe4s] (
    private val metric: JavaGauge,
    private val encoder: LabelEncoder[A]
) {

  /** Executes immediately and may throw a Prometheus client exception. */
  def set(labels: A, value: Double): Unit =
    metric.labelValues(MetricSupport.values(encoder, labels): _*).set(value)

  /** Executes immediately and may throw a Prometheus client exception. */
  def inc(labels: A): Unit = metric.labelValues(MetricSupport.values(encoder, labels): _*).inc()

  /** Executes immediately and may throw a Prometheus client exception. */
  def incBy(labels: A, amount: Double): Unit =
    metric.labelValues(MetricSupport.values(encoder, labels): _*).inc(amount)

  /** Executes immediately and may throw a Prometheus client exception. */
  def dec(labels: A): Unit = metric.labelValues(MetricSupport.values(encoder, labels): _*).dec()

  /** Executes immediately and may throw a Prometheus client exception. */
  def decBy(labels: A, amount: Double): Unit =
    metric.labelValues(MetricSupport.values(encoder, labels): _*).dec(amount)

  def set(value: Double)(implicit ev: A =:= Unit): Unit = set(ev.flip(()), value)
  def inc(implicit ev: A =:= Unit): Unit = inc(ev.flip(()))
  def dec(implicit ev: A =:= Unit): Unit = dec(ev.flip(()))
}

final class BoundGauge[F[_]] private[promsafe4s] (
    get0: () => Double,
    set0: Double => Unit,
    inc0: () => Unit,
    incBy0: Double => Unit,
    dec0: () => Unit,
    decBy0: Double => Unit
)(implicit F: Sync[F]) {
  def get: F[Double] = F.delay(get0())
  def set(value: Double): F[Unit] = F.delay(set0(value))
  def inc: F[Unit] = F.delay(inc0())
  def incBy(amount: Double): F[Unit] = F.delay(incBy0(amount))
  def dec: F[Unit] = F.delay(dec0())
  def decBy(amount: Double): F[Unit] = F.delay(decBy0(amount))
}
