package promsafe4s

import cats.effect.kernel.{Resource, Sync}
import io.prometheus.metrics.core.metrics.{Counter => JavaCounter}
import io.prometheus.metrics.model.registry.PrometheusRegistry

final case class CounterBuilder[F[_], A] private[promsafe4s] (
    name: String,
    help: String,
    encoder: LabelEncoder[A],
    customize: JavaCounter.Builder => JavaCounter.Builder
)(implicit F: Sync[F]) {
  def labels[B](next: LabelEncoder[B]): CounterBuilder[F, B] =
    copy(encoder = next)

  def customizeWith(f: JavaCounter.Builder => JavaCounter.Builder): CounterBuilder[F, A] =
    copy(customize = customize.andThen(f))

  /** Executes immediately and may throw a Prometheus client exception. */
  def unsafeRegistration(registry: PrometheusRegistry): Counter[F, A] = {
    val builder = customize(JavaCounter.builder().name(name).help(help))
    builder.labelNames(encoder.names.toArray: _*)
    val metric = builder.register(registry)
    new Counter[F, A](metric, encoder)
  }

  def register(registry: PrometheusRegistry): F[Counter[F, A]] = F.delay(unsafeRegistration(registry))

  def resource(registry: PrometheusRegistry): Resource[F, Counter[F, A]] =
    Resource.make(register(registry))(counter => F.delay(registry.unregister(counter.metric)))
}

object Counter {
  def builder[F[_]](name: String, help: String)(implicit F: Sync[F]): CounterBuilder[F, Unit] =
    CounterBuilder(name, help, LabelEncoder.empty[Unit], builder => builder)
}

final class Counter[F[_], A] private[promsafe4s] (
    private[promsafe4s] val metric: JavaCounter,
    private val encoder: LabelEncoder[A]
)(implicit F: Sync[F]) {
  lazy val unsafe: UnsafeCounter[A] = new UnsafeCounter[A](metric, encoder)

  def inc(labels: A): F[Unit] = F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).inc())

  def incBy(labels: A, amount: Double): F[Unit] =
    F.delay(metric.labelValues(MetricSupport.values(encoder, labels): _*).inc(amount))

  def inc(amount: Double)(implicit ev: A =:= Unit): F[Unit] = incBy(ev.flip(()), amount)

  def inc(implicit ev: A =:= Unit): F[Unit] = incBy(ev.flip(()), 1.0)

  def bind(labels: A): F[BoundCounter[F]] = F.delay {
    val point = metric.labelValues(MetricSupport.values(encoder, labels): _*)
    new BoundCounter[F](() => point.inc(), amount => point.inc(amount))
  }
}

final class UnsafeCounter[A] private[promsafe4s] (
    private val metric: JavaCounter,
    private val encoder: LabelEncoder[A]
) {

  /** Executes immediately and may throw a Prometheus client exception. */
  def inc(labels: A): Unit = metric.labelValues(MetricSupport.values(encoder, labels): _*).inc()

  /** Executes immediately and may throw a Prometheus client exception. */
  def incBy(labels: A, amount: Double): Unit =
    metric.labelValues(MetricSupport.values(encoder, labels): _*).inc(amount)

  def inc(amount: Double)(implicit ev: A =:= Unit): Unit = incBy(ev.flip(()), amount)

  def inc(implicit ev: A =:= Unit): Unit = incBy(ev.flip(()), 1.0)
}

final class BoundCounter[F[_]] private[promsafe4s] (
    inc0: () => Unit,
    incBy0: Double => Unit
)(implicit F: Sync[F]) {
  def inc: F[Unit] = F.delay(inc0())
  def incBy(amount: Double): F[Unit] = F.delay(incBy0(amount))
}
