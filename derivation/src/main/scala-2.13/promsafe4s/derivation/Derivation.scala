package promsafe4s.derivation

import promsafe4s.{Label, LabelEncoder}
import scala.reflect.ClassTag

/**
  * Scala 2.13 fallback for semiautomatic product derivation.
  *
  * Scala 2 Magnolia's macro entry point cannot be used from this cross-build
  * with the current compiler/toolchain, so this adapter uses the stable
  * Product field-name API. Explicit LabelEncoder values remain available when
  * typed Show rendering or custom names are required.
  */
object semiauto {
  def deriveLabelEncoder[A <: Product: ClassTag]: LabelEncoder[A] = {
    val names = ProductFieldNames.names[A]
    names.zipWithIndex.foldLeft(LabelEncoder.empty[A]) {
      case (encoder, (name, index)) =>
        encoder.label(name)(value => String.valueOf(value.productElement(index)))
    }
  }
}

private object ProductFieldNames {
  def names[A <: Product: ClassTag]: Vector[String] =
    implicitly[ClassTag[A]].runtimeClass.getDeclaredFields.toVector
      .map(_.getName)
      .filterNot(_.contains("$"))
}
