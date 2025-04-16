package ac.th.cmu.cs.core.model

import ac.th.cmu.cs.BaseSpec
import java.time.Instant

class TransactionSpec extends BaseSpec {

  "Transaction case class" should {
    "be creatable with basic fields including signature and public key" in { // <--- Update description slightly
      val now = Instant.now()
      val entityInfo = ProvEntityInfo("entity:123", "MilkBatch", Map("volume" -> "1000L"))
      // เพิ่ม publicKey และ signature เข้าไปตอนสร้าง
      val pk = "test-public-key-123"
      val sig = "test-signature-abc"
      val tx = Transaction(
        id = "tx-abc",
        timestamp = now,
        transactionType = "CREATE_BATCH",
        provEntity = Some(entityInfo),
        provActivity = None,
        provAgent = None,
        attributes = Map("location" -> "Farm A"),
        publicKey = pk, // <-- Field ใหม่
        signature = sig   // <-- Field ใหม่
      )

      tx.id shouldBe "tx-abc"
      tx.timestamp shouldBe now
      tx.transactionType shouldBe "CREATE_BATCH"
      tx.provEntity shouldBe Some(entityInfo)
      tx.attributes("location") shouldBe "Farm A"
      tx.publicKey shouldBe pk // <--- เพิ่ม Assertion
      tx.signature shouldBe sig   // <--- เพิ่ม Assertion
    }

    // TODO: Add more tests for:
    // - Equality checks
    // - Copy method usage
    // - Validation logic (if any is added to Transaction or its creation)
  }
}