package ac.th.cmu.cs

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures // Optional: for async testing
import org.scalatest.time.{Millis, Seconds, Span} // Optional: for async testing config

/**
 * Base trait for tests using ScalaTest AnyWordSpec style with ShouldMatchers.
 */
trait BaseSpec extends AnyWordSpec with Matchers with ScalaFutures {

  // Optional: Default configuration for futures in tests
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

}