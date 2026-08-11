package promsafe4s

import cats.{Contravariant, Show}

final class Label[A] private[promsafe4s] (val name: String, private[promsafe4s] val render: A => String) {
  private[promsafe4s] def contramap[B](f: B => A): Label[B] =
    new Label[B](name, value => render(f(value)))
}

object Label {
  def apply[A, B: Show](name: String)(extract: A => B): Label[A] =
    new Label[A](name, value => Show[B].show(extract(value)))
}

final class LabelEncoder[A] private (private[promsafe4s] val labels: Vector[Label[A]]) {
  private[promsafe4s] val names: Vector[String] = labels.map(_.name)

  def labelNames: Vector[String] = names

  private[promsafe4s] def values(value: A): Vector[String] =
    labels.map(_.render(value))

  def contramap[B](f: B => A): LabelEncoder[B] =
    new LabelEncoder[B](labels.map(_.contramap(f)))

  def label[B: Show](name: String)(extract: A => B): LabelEncoder[A] =
    new LabelEncoder[A](labels :+ Label[A, B](name)(extract))
}

object LabelEncoder {
  def apply[A]: LabelEncoder[A] = empty[A]

  def empty[A]: LabelEncoder[A] = new LabelEncoder[A](Vector.empty)

  def of[A](first: Label[A], rest: Label[A]*): LabelEncoder[A] =
    new LabelEncoder[A](first +: rest.toVector)

  implicit val contravariantInstance: Contravariant[LabelEncoder] =
    new Contravariant[LabelEncoder] {
      override def contramap[A, B](fa: LabelEncoder[A])(f: B => A): LabelEncoder[B] =
        fa.contramap(f)
    }
}
