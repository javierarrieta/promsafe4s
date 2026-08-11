package promsafe4s.derivation

import cats.Show
import munit.FunSuite

final class DerivationScala3Suite extends FunSuite {
  test("Scala 3 derivation uses Show for field values") {
    final case class Status(value: Int)
    implicit val statusShow: Show[Status] = Show.show(status => s"status-${status.value}")
    final case class Labels(status: Status)

    val encoder = semiauto.deriveLabelEncoder[Labels]
    assertEquals(encoder.labelNames, Vector("status"))
    assertEquals(encoder.values(Labels(Status(7))), Vector("status-7"))
  }
}
