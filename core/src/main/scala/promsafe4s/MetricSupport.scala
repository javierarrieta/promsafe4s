package promsafe4s

import cats.effect.kernel.Sync
import io.prometheus.metrics.model.registry.PrometheusRegistry

private[promsafe4s] object MetricSupport {
  def values[A](encoder: LabelEncoder[A], labels: A): Array[String] =
    encoder.values(labels).toArray

  def register[F[_], A, J](
      F: Sync[F],
      registry: PrometheusRegistry,
      encoder: LabelEncoder[A],
      names: Array[String]
  )(build: Array[String] => J)(register: J => Unit): F[Unit] =
    F.delay {
      val metric = build(names)
      register(metric)
    }
}
