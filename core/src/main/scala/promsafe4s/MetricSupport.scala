package promsafe4s

private[promsafe4s] object MetricSupport {
  def values[A](encoder: LabelEncoder[A], labels: A): Array[String] =
    encoder.values(labels).toArray
}
