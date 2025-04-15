package ac.th.cmu.cs.infrastructure.serialization

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import io.circe.parser._
import io.circe.syntax._ // Enables .asJson method
import io.circe.DecodingFailure // <--- เพิ่ม Import
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
      val originalTx = Transaction(
        id = "tx-json-test",
        timestamp = now,
        transactionType = "MILKING_EVENT",
        provEntity = Some(entity),
        provActivity = Some(activity),
        provAgent = Some(agent),
        attributes = Map("weather" -> "Sunny", "batch_quality" -> "A+"),
        signature = "json-sig-123",
        publicKey = "json-pk-456"
      )

      val jsonString: String = originalTx.asJson.noSpaces
      val decodeResult = decode[Transaction](jsonString) // <--- แก้ไข: เอา Type Annotation ออก

      decodeResult should be(Symbol("right"))
      decodeResult.foreach { decodedTx =>
        decodedTx shouldBe originalTx
        decodedTx.id shouldBe "tx-json-test"
        decodedTx.timestamp shouldBe now
        decodedTx.provEntity shouldBe Some(entity)
        decodedTx.provActivity.flatMap(_.startTime) shouldBe Some(now.minusSeconds(60))
        decodedTx.attributes("weather") shouldBe "Sunny"
      }
    }

    "correctly encode and decode Block" in {
      val now = Instant.now()
      val tx1 = Transaction("txA", now.minusSeconds(10), "TYPE_A", None, None, None, Map(), "sigA", "pkA")
      val tx2 = Transaction("txB", now.minusSeconds(5), "TYPE_B", None, None, None, Map("ref" -> "txA"), "sigB", "pkB")

      val originalBlock = Block(
        hash = "blockhash-json-test",
        prevHashes = List("prev1", "prev2"),
        transactions = List(tx1, tx2),
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
      val decodeResultBlock = decode[Block](jsonString) // <--- แก้ไข: เอา Type Annotation ออก และเปลี่ยนชื่อ

      decodeResultBlock should be(Symbol("right"))
      decodeResultBlock.foreach { decodedBlock =>
        decodedBlock shouldBe originalBlock
        decodedBlock.hash shouldBe "blockhash-json-test"
        decodedBlock.prevHashes should contain theSameElementsAs List("prev1", "prev2")
        decodedBlock.transactions.size shouldBe 2
        decodedBlock.transactions.find(_.id == "txB").map(_.attributes) shouldBe Some(Map("ref" -> "txA"))
        decodedBlock.timestamp shouldBe now
        decodedBlock.height shouldBe 101L
        decodedBlock.supplyChainType shouldBe None
        decodedBlock.metadata("region") shouldBe "North"
      }
    }

    "fail decoding with invalid JSON structure" in {
      val invalidJson = """{"id":"tx-invalid","timestamp":"not-a-date"}"""
      val decodeResultInvalid = decode[Transaction](invalidJson) // <--- แก้ไข: เปลี่ยนชื่อตัวแปร

      decodeResultInvalid should be(Symbol("left"))
      decodeResultInvalid.left.foreach { error =>
         error.isInstanceOf[DecodingFailure] shouldBe true // <--- แก้ไข: ใช้วิธีตรวจสอบ Type แบบนี้
      }
    }
  }
}