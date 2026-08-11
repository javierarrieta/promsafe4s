package promsafe4s.derivation

import cats.Show
import magnolia1.{CaseClass, SealedTrait}
import promsafe4s.{Label, LabelEncoder}
import scala.deriving.Mirror

private[derivation] trait DerivedField[A] {
  def render(value: A): String
  def labelEncoder: LabelEncoder[A]
}

private[derivation] object DerivedField {
  implicit def fromShow[A](using show: Show[A]): DerivedField[A] =
    new DerivedField[A] {
      override def render(value: A): String = show.show(value)
      override val labelEncoder: LabelEncoder[A] = LabelEncoder.empty[A]
    }
}

private[derivation] object Derivation extends magnolia1.Derivation[DerivedField] {
  override def join[T](ctx: CaseClass[DerivedField, T]): DerivedField[T] = {
    val labels: Vector[Label[T]] = ctx.params.toVector.map { param =>
      Label[T, String](param.label)(value => param.typeclass.render(param.deref(value)))
    }

    new DerivedField[T] {
      private val encoder = labels match {
        case head +: tail => LabelEncoder.of(head, tail: _*)
        case _            => LabelEncoder.empty[T]
      }

      override def render(value: T): String = ""

      override val labelEncoder: LabelEncoder[T] = encoder
    }
  }

  override def split[T](ctx: SealedTrait[DerivedField, T]): DerivedField[T] =
    throw new IllegalArgumentException("LabelEncoder derivation supports case classes only")

  inline def derive[T](using Mirror.Of[T]): LabelEncoder[T] = this.derived[T].labelEncoder
}

object semiauto {
  inline def deriveLabelEncoder[A](using Mirror.Of[A]): LabelEncoder[A] = Derivation.derive[A]
}
