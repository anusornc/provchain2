package ac.th.cmu.cs.infrastructure.serialization

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import io.circe.parser._
import io.circe.syntax._ // Enables .asJson method
import java.time.Instant

// Import the codecs into scope
import JsonCodecs._

class JsonCodecsSpec extends BaseSpec {

  "JsonCodecs" should {

    "correctly encode and decode Transaction" in {
      val now = Instant.now()
      // Create a reasonably complex transaction
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

      // Encode to JSON string
      val jsonString: String = originalTx.asJson.noSpaces // .noSpaces for compact string output

      // Decode back from JSON string
      val decodeResult: Either[Error, Transaction] = decode[Transaction](jsonString)

      // Assertions
      decodeResult should be(Symbol("right")) // Check decoding was successful
      decodeResult.foreach { decodedTx =>
        decodedTx shouldBe originalTx // Check if the decoded object equals the original
        // Optionally check specific fields again
        decodedTx.id shouldBe "tx-json-test"
        decodedTx.timestamp shouldBe now // Instant comparison should work
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
        supplyChainType = None, // Test Option None case
        dagWeight = 1000L,
        metadata = Map("region" -> "North", "processed_by" -> "NodeX")
      )

      // Encode to JSON string
      val jsonString: String = originalBlock.asJson.noSpaces

      // Decode back from JSON string
      val decodeResult: Either[Error, Block] = decode[Block](jsonString)

      // Assertions
      decodeResult should be(Symbol("right"))
      decodeResult.foreach { decodedBlock =>
        decodedBlock shouldBe originalBlock // Check full object equality
        // Optionally check specific fields
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
      val decodeResult = decode[Transaction](invalidJson)

      decodeResult should be(Symbol("left")) // Check decoding failed
      decodeResult.left.foreach { error =>
        // You can add more specific error checking if needed
        error shouldBe a[DecodingFailure]
      }
    }
  }
}