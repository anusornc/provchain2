package ac.th.cmu.cs.core.model

import ac.th.cmu.cs.BaseSpec
import java.time.Instant

class TransactionSpec extends BaseSpec {

  "Transaction case class" should {
    "be creatable with basic fields" in {
      val now = Instant.now()
      val entityInfo = ProvEntityInfo("entity:123", "MilkBatch", Map("volume" -> "1000L"))
      val tx = Transaction(
        id = "tx-abc",
        timestamp = now,
        transactionType = "CREATE_BATCH",
        provEntity = Some(entityInfo),
        provActivity = None,
        provAgent = None,
        attributes = Map("location" -> "Farm A"),
        signature = "dummy-signature",
        publicKey = "dummy-pubkey"
      )

      tx.id shouldBe "tx-abc"
      tx.timestamp shouldBe now
      tx.transactionType shouldBe "CREATE_BATCH"
      tx.provEntity shouldBe Some(entityInfo)
      tx.attributes("location") shouldBe "Farm A"
      tx.signature shouldBe "dummy-signature"
      tx.publicKey shouldBe "dummy-pubkey"
    }

    // TODO: Add more tests for:
    // - Equality checks
    // - Copy method usage
    // - Validation logic (if any is added to Transaction or its creation)
  }
}