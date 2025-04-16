package ac.th.cmu.cs.infrastructure.serialization

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import io.circe.parser._
import io.circe.syntax._
import io.circe.DecodingFailure
import java.time.Instant

// Import the codecs into scope
import JsonCodecs._

class JsonCodecsSpec extends BaseSpec {

  "JsonCodecs" should {

    "correctly encode and decode Transaction" in {
      val now = Instant.now()
      val entity = ProvEntityInfo("ent-1", "MilkBatch", Map("farm" -> "Farm A"))
      val activity = ProvActivityInfo("act-1", "Milking", Some(now.minusSeconds(60)), Some(now), Some("Barn 3"))
      val agent = ProvAgentInfo("agent-1", "Farmer", Some("Mr. Jones"))
      // เพิ่ม publicKey และ signature
      val pk = "json-pk-456"
      val sig = "json-sig-123"
      val originalTx = Transaction(
        id = "tx-json-test",
        timestamp = now,
        transactionType = "MILKING_EVENT",
        provEntity = Some(entity),
        provActivity = Some(activity),
        provAgent = Some(agent),
        attributes = Map("weather" -> "Sunny", "batch_quality" -> "A+"),
        publicKey = pk, // <-- Field ใหม่
        signature = sig   // <-- Field ใหม่
      )

      val jsonString: String = originalTx.asJson.noSpaces
      val decodeResult = decode[Transaction](jsonString)

      decodeResult should be(Symbol("right"))
      decodeResult.foreach { decodedTx =>
        decodedTx shouldBe originalTx
        // ... (Assertions เดิม) ...
        decodedTx.provActivity.flatMap(_.startTime) shouldBe Some(now.minusSeconds(60))
        decodedTx.attributes("weather") shouldBe "Sunny"
        // เพิ่ม Assertions ใหม่
        decodedTx.publicKey shouldBe pk
        decodedTx.signature shouldBe sig
      }
    }

    "correctly encode and decode Block" in {
      val now = Instant.now()
      // อัปเดตการสร้าง tx1, tx2 ให้มี field ใหม่
      val tx1 = Transaction("txA", now.minusSeconds(10), "TYPE_A", None, None, None, Map(), "pkA", "sigA")
      val tx2 = Transaction("txB", now.minusSeconds(5), "TYPE_B", None, None, None, Map("ref" -> "txA"), "pkB", "sigB")

      val originalBlock = Block(
        hash = "blockhash-json-test",
        prevHashes = List("prev1", "prev2"),
        transactions = List(tx1, tx2), // ใช้ tx1, tx2 ที่อัปเดตแล้ว
        merkleRoot = "merkle-json-test",
        timestamp = now,
        height = 101L,
        validator = "validator-json-pk",
        signature = "blocksig-json-test",
        supplyChainType = None,
        dagWeight = 1000L,
        metadata = Map("region" -> "North", "processed_by" -> "NodeX")
      )

      val jsonString: String = originalBlock.asJson.noSpaces
      val decodeResultBlock = decode[Block](jsonString)

      decodeResultBlock should be(Symbol("right"))
      decodeResultBlock.foreach { decodedBlock =>
        decodedBlock shouldBe originalBlock
        // ... (Assertions เดิม) ...
        decodedBlock.transactions.size shouldBe 2
        // เช็ค field ใหม่ใน Transaction ที่ decode มา
        decodedBlock.transactions.find(_.id == "txA").map(_.publicKey) shouldBe Some("pkA")
        decodedBlock.transactions.find(_.id == "txB").map(_.signature) shouldBe Some("sigB")
        // ... (Assertions เดิม) ...
      }
    }

    "fail decoding with invalid JSON structure" in {
      // Test case นี้เหมือนเดิม ไม่ต้องแก้ เพราะทดสอบโครงสร้าง JSON ผิดพลาด
      val invalidJson = """{"id":"tx-invalid","timestamp":"not-a-date"}"""
      val decodeResultInvalid = decode[Transaction](invalidJson)

      decodeResultInvalid should be(Symbol("left"))
      decodeResultInvalid.left.foreach { error =>
         error.isInstanceOf[DecodingFailure] shouldBe true
      }
    }
  }
}