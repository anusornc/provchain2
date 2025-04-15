package ac.th.cmu.cs.infrastructure.persistence

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.persistence.PersistenceError
import java.time.Instant
import org.scalatest.BeforeAndAfterEach // Import สำหรับ beforeEach

class InMemoryBlockRepositorySpec extends BaseSpec with BeforeAndAfterEach {

  // สร้าง Repository ใหม่สำหรับแต่ละ Test case เพื่อความสะอาด
  var repository: InMemoryBlockRepository = _

  // ใช้ beforeEach เพื่อสร้าง instance ใหม่และ clear ข้อมูลก่อนแต่ละ test
  override def beforeEach(): Unit = {
    repository = new InMemoryBlockRepository()
    repository.clearAll() // เคลียร์ข้อมูลเพื่อให้แต่ละ test ไม่ปนกัน
  }

  // --- Helper Data for Tests ---
  val now: Instant = Instant.now()
  def createTestTx(id: String, time: Instant = now): Transaction =
    Transaction(id, time, s"TYPE_$id", None, None, None, Map("attr" -> id), s"sig_$id", s"pk_$id")

  def createTestBlock(hash: String, height: Long, prev: List[String], txs: List[Transaction], time: Instant = now): Block =
    Block(hash, prev, txs, s"mr_$hash", time, height, s"val_$hash", s"sig_$hash", Some(s"SUPPLY_$hash"), height * 10, Map("meta" -> hash))

  val tx1: Transaction = createTestTx("tx1", now.minusSeconds(10))
  val tx2: Transaction = createTestTx("tx2", now.minusSeconds(5))
  val tx3: Transaction = createTestTx("tx3", now)

  val block1: Block = createTestBlock("h1", 1L, List("h0"), List(tx1), now.minusSeconds(20))
  val block2: Block = createTestBlock("h2", 2L, List("h1"), List(tx2), now.minusSeconds(10))
  val block3: Block = createTestBlock("h3", 2L, List("h1"), List(tx3), now.minusSeconds(5)) // Height เดียวกับ block2
  val block4: Block = createTestBlock("h4", 3L, List("h2", "h3"), List(), now)
  // --- End Helper Data ---


  "InMemoryBlockRepository" should {

    // --- Testing saveBlock / getBlockByHash ---
    "save a block and retrieve it by hash" in {
      val saveResult = repository.saveBlock(block1)
      saveResult should be(Right(()))

      val getResult = repository.getBlockByHash("h1")
      getResult should be(Right(Some(block1)))
    }

    "return None when getting a non-existent block hash" in {
      val getResult = repository.getBlockByHash("non-existent-hash")
      getResult should be(Right(None))
    }

    "overwrite block if saved with the same hash" in {
      repository.saveBlock(block1) // Save initial
      val modifiedBlock1 = block1.copy(dagWeight = 99L) // Modify something
      val saveResult = repository.saveBlock(modifiedBlock1) // Save again with same hash 'h1'
      saveResult should be(Right(()))

      val getResult = repository.getBlockByHash("h1")
      getResult should be(Right(Some(modifiedBlock1))) // Should get the modified one
    }

    // --- Testing saveBlock / findTransactionById ---
    "find a transaction by ID after its block is saved" in {
      repository.saveBlock(block1) // Contains tx1
      repository.saveBlock(block2) // Contains tx2

      val findTx1Result = repository.findTransactionById("tx1")
      findTx1Result should be(Right(Some(tx1)))

      val findTx2Result = repository.findTransactionById("tx2")
      findTx2Result should be(Right(Some(tx2)))
    }

    "return None when finding a non-existent transaction ID" in {
      repository.saveBlock(block1)
      val findResult = repository.findTransactionById("non-existent-tx")
      findResult should be(Right(None))
    }

     "find the latest transaction if multiple blocks contain the same tx ID (last write wins)" in {
      val txDuplicateId = "tx-dup"
      val txV1 = createTestTx(txDuplicateId, now.minusSeconds(10)).copy(attributes = Map("version" -> "1"))
      val txV2 = createTestTx(txDuplicateId, now.minusSeconds(5)).copy(attributes = Map("version" -> "2"))
      val blockV1 = createTestBlock("hv1", 1L, List("h0"), List(txV1), now.minusSeconds(10))
      val blockV2 = createTestBlock("hv2", 2L, List("hv1"), List(txV2), now.minusSeconds(5))

      repository.saveBlock(blockV1)
      repository.saveBlock(blockV2) // Save block containing the newer version of the tx

      val findResult = repository.findTransactionById(txDuplicateId)
      findResult should be(Right(Some(txV2))) // Should find the version from the last block saved
    }


    // --- Testing saveBlock / getBlocksByHeight ---
    "retrieve blocks by height" in {
      repository.saveBlock(block1) // height 1
      repository.saveBlock(block2) // height 2
      repository.saveBlock(block3) // height 2
      repository.saveBlock(block4) // height 3

      // Height 1
      val height1Result = repository.getBlocksByHeight(1L)
      height1Result should be(Right(List(block1)))

      // Height 2
      val height2Result = repository.getBlocksByHeight(2L)
      height2Result should be(Symbol("right"))
      // ใช้ contain theSameElementsAs เพราะลำดับอาจไม่แน่นอน
      height2Result.getOrElse(List.empty) should contain theSameElementsAs List(block2, block3)

      // Height 3
      val height3Result = repository.getBlocksByHeight(3L)
      height3Result should be(Right(List(block4)))
    }

    "return an empty list when getting blocks for a non-existent height" in {
       repository.saveBlock(block1)
       val getResult = repository.getBlocksByHeight(99L)
       getResult should be(Right(List.empty))
    }


    // --- Testing Tip Management ---
    "return an empty list initially for tip hashes" in {
      val tipsResult = repository.getTipHashes()
      tipsResult should be(Right(List.empty))
    }

    "add and retrieve tip hashes" in {
      repository.addTipHash("tip1") should be(Right(()))
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip1")

      repository.addTipHash("tip2") should be(Right(()))
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip1", "tip2")

      repository.addTipHash("tip1") should be(Right(())) // Adding same again
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip1", "tip2")
    }

    "remove tip hashes" in {
      repository.addTipHash("tip1")
      repository.addTipHash("tip2")
      repository.addTipHash("tip3")

      repository.removeTipHash("tip2") should be(Right(()))
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip1", "tip3")

      repository.removeTipHash("tip1") should be(Right(()))
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip3")

      repository.removeTipHash("tip3") should be(Right(()))
      repository.getTipHashes() should be(Right(List.empty))
    }

    "removing a non-existent tip hash should succeed without error" in {
      repository.addTipHash("tip1")
      repository.removeTipHash("non-existent-tip") should be(Right(()))
      repository.getTipHashes().getOrElse(List.empty) should contain theSameElementsAs List("tip1")
    }
  }
}