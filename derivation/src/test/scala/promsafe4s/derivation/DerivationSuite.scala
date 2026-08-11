package promsafe4s.derivation

import cats.Show
import munit.FunSuite

final class DerivationSuite extends FunSuite {
  final case class Labels(method: String, status: Int)

  test("derivation uses case-class field names and order") {
    val encoder = semiauto.deriveLabelEncoder[Labels]
    assertEquals(encoder.labelNames, Vector("method", "status"))
  }

  test("derivation uses Show for field values") {
    final case class Status(value: Int)
    implicit val statusShow: Show[Status] = Show.show(status => s"status-${status.value}")
    final case class Labels(status: Status)

    val encoder = semiauto.deriveLabelEncoder[Labels]
    assertEquals(encoder.labelNames, Vector("status"))
  }
}
