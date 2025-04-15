package ac.th.cmu.cs.core.model

import ac.th.cmu.cs.BaseSpec
import java.time.Instant

class BlockSpec extends BaseSpec {

  "Block case class" should {
    "be creatable with basic fields and transactions" in {
      val now = Instant.now()
      val tx1 = Transaction("tx1", now.minusSeconds(10), "TYPE_A", None, None, None, Map(), "sig1", "pk1")
      val tx2 = Transaction("tx2", now.minusSeconds(5), "TYPE_B", None, None, None, Map(), "sig2", "pk2")

      val block = Block(
        hash = "blockhash123",
        prevHashes = List("prevhash0"),
        transactions = List(tx1, tx2),
        merkleRoot = "merkleABC",
        timestamp = now,
        height = 1L,
        validator = "validatorPK",
        signature = "blockSigXYZ",
        supplyChainType = Some("INITIAL"),
        dagWeight = 10L,
        metadata = Map("node" -> "node-1")
      )

      block.hash shouldBe "blockhash123"
      block.prevHashes shouldBe List("prevhash0")
      block.transactions.size shouldBe 2
      block.transactions should contain theSameElementsAs List(tx1, tx2)
      block.merkleRoot shouldBe "merkleABC"
      block.timestamp shouldBe now
      block.height shouldBe 1L
      block.validator shouldBe "validatorPK"
      block.signature shouldBe "blockSigXYZ"
      block.supplyChainType shouldBe Some("INITIAL")
      block.dagWeight shouldBe 10L
      block.metadata("node") shouldBe "node-1"
    }

    // TODO: Add more tests for:
    // - Equality checks
    // - Copy method usage
    // - Scenarios with empty transaction lists or prevHashes
  }
}