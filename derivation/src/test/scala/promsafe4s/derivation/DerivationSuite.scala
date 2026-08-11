package promsafe4s.derivation

import munit.FunSuite

private final case class Labels(method: String, status: Int)

final class DerivationSuite extends FunSuite {
  test("derivation uses case-class field names and order") {
    val encoder = semiauto.deriveLabelEncoder[Labels]
    assertEquals(encoder.labelNames, Vector("method", "status"))
  }
}
