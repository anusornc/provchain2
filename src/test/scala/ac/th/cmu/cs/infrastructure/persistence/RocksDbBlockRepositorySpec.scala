package ac.th.cmu.cs.infrastructure.persistence

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.persistence.PersistenceError
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterEach
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.Comparator
import java.io.File
// *** เพิ่ม Imports ***
import scala.util.Using
import scala.util.Try
// *** จบ Imports ***

class RocksDbBlockRepositorySpec extends BaseSpec with BeforeAndAfterEach {

  var testDbPath: Path = _
  var config: Config = _
  var repository: RocksDbBlockRepository = _

  // --- Helper สำหรับลบ Directory ---
  def deleteDirectoryRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      // ใช้ Using.resource เพื่อให้ Stream ถูกปิดเสมอ
      Using.resource(Files.walk(path)) { walk =>
         walk.sorted(Comparator.reverseOrder())
           .map[File](_.toFile)
           .forEach(_.delete())
       }
       // ใช้ Try ครอบการลบ root เผื่อมีปัญหา
       Try(Files.deleteIfExists(path))
       println(s"Cleaned up test directory: $path")
    }
  }

  // --- Setup & Teardown ---
  override def beforeEach(): Unit = {
    testDbPath = Files.createTempDirectory("provchain_rocksdb_test_")
    println(s"Created test directory: $testDbPath")
    config = ConfigFactory.empty()
      .withValue("rocksdb.path", ConfigValueFactory.fromAnyRef(testDbPath.toString))
      .withValue("rocksdb.create-if-missing", ConfigValueFactory.fromAnyRef(true))
    repository = new RocksDbBlockRepository(config)
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    try {
      if (repository != null) {
        repository.close()
        println(s"Closed repository for: $testDbPath")
      }
    } finally {
      deleteDirectoryRecursively(testDbPath)
      super.afterEach()
    }
  }

  // --- Helper Data for Tests --- (เหมือนเดิม)
  val now: Instant = Instant.now()
  def createTestTx(id: String, pk: String = "aa", sig: String = "bb", time: Instant = now): Transaction = Transaction(id, time, s"TYPE_$id", None, None, None, Map("attr" -> id), pk, sig)
  def createTestBlock(hash: String, height: Long, prev: List[String], txs: List[Transaction], time: Instant = now): Block = Block(hash, prev, txs, s"mr_$hash", time, height, s"val_$hash", s"sig_$hash", Some(s"SUPPLY_$hash"), height * 10, Map("meta" -> hash))
  val tx1: Transaction = createTestTx("tx1", "pk1", "sig1", now.minusSeconds(10))
  val tx2: Transaction = createTestTx("tx2", "pk2", "sig2", now.minusSeconds(5))
  val block1: Block = createTestBlock("h1", 1L, List("h0"), List(tx1), now.minusSeconds(20))
  val block2: Block = createTestBlock("h2", 2L, List("h1"), List(tx2), now.minusSeconds(10))


  "RocksDbBlockRepository" should {

    "initialize successfully and create the database directory" in {
      repository shouldNot be (null)
      Files.exists(testDbPath) shouldBe true
      repository.getBlockByHash("somehash") shouldBe Right(None)
    }

    "save a block and retrieve it by hash" in {
      val saveResult = repository.saveBlock(block1)
      saveResult should be(Right(()))
      repository.close() // ปิด DB เดิม

      val readerRepo = new RocksDbBlockRepository(config) // สร้างใหม่
      val getResult = readerRepo.getBlockByHash(block1.hash)
      getResult should be(Right(Some(block1)))
      readerRepo.close() // ปิดตัวอ่าน
    }

    "return None when getting a non-existent block hash" in {
      val getResult = repository.getBlockByHash("non-existent-hash")
      getResult should be(Right(None))
    }

     "overwrite block if saved with the same hash" in {
      repository.saveBlock(block1) shouldBe Right(())
      val modifiedBlock1 = block1.copy(dagWeight = 999L)
      val saveResult = repository.saveBlock(modifiedBlock1)
      saveResult shouldBe Right(())
      repository.close()

      val readerRepo = new RocksDbBlockRepository(config)
      val getResult = readerRepo.getBlockByHash(block1.hash)
      getResult should be(Right(Some(modifiedBlock1)))
      readerRepo.close()
    }

    "find a transaction by ID after its block is saved" in {
      repository.saveBlock(block1) shouldBe Right(())
      repository.saveBlock(block2) shouldBe Right(())
      // อ่านจาก Instance เดิมได้
      repository.findTransactionById(tx1.id) shouldBe Right(Some(tx1))
      repository.findTransactionById(tx2.id) shouldBe Right(Some(tx2))
    }

     "return None when finding a non-existent transaction ID" in {
      repository.saveBlock(block1) shouldBe Right(())
      repository.findTransactionById("non-existent-tx") shouldBe Right(None)
    }

     "find the latest transaction if multiple blocks contain the same tx ID (last write wins)" in {
      val txDuplicateId = "tx-dup"
      val txV1 = createTestTx(txDuplicateId, pk="pkV1", sig="sigV1").copy(attributes = Map("version" -> "1"))
      val txV2 = createTestTx(txDuplicateId, pk="pkV2", sig="sigV2").copy(attributes = Map("version" -> "2"))
      val blockV1 = createTestBlock("hv1", 1L, List("h0"), List(txV1))
      val blockV2 = createTestBlock("hv2", 2L, List("hv1"), List(txV2))
      repository.saveBlock(blockV1) shouldBe Right(())
      repository.saveBlock(blockV2) shouldBe Right(())
      repository.findTransactionById(txDuplicateId) shouldBe Right(Some(txV2))
    }

    "retrieve blocks by height" in {
      val tx3 = createTestTx("tx3", "pk3", "sig3")
      val block3 = createTestBlock("h3", 2L, List("h1"), List(tx3), now.minusSeconds(5))
      val block4 = createTestBlock("h4", 3L, List("h2", "h3"), List(), now)
      repository.saveBlock(block1) shouldBe Right(())
      repository.saveBlock(block2) shouldBe Right(())
      repository.saveBlock(block3) shouldBe Right(())
      repository.saveBlock(block4) shouldBe Right(())

      repository.getBlocksByHeight(1L) shouldBe Right(List(block1))
      repository.getBlocksByHeight(2L).map(_.toSet) shouldBe Right(Set(block2, block3))
      repository.getBlocksByHeight(3L) shouldBe Right(List(block4))
    }

     "return an empty list when getting blocks for a non-existent height" in {
       repository.saveBlock(block1) shouldBe Right(())
       repository.getBlocksByHeight(99L) shouldBe Right(List.empty)
     }

    // --- Testing Tip Management ---
     "return an empty list initially for tip hashes" in {
      repository.getTipHashes() shouldBe Right(List.empty)
    }

    "add and retrieve tip hashes" in {
      repository.addTipHash("tip1") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip1"))
      repository.addTipHash("tip2") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip1", "tip2"))
      repository.addTipHash("tip1") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip1", "tip2"))
    }

    "remove tip hashes" in {
      repository.addTipHash("tip1"); repository.addTipHash("tip2"); repository.addTipHash("tip3")
      repository.removeTipHash("tip2") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip1", "tip3"))
      repository.removeTipHash("tip1") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip3"))
      repository.removeTipHash("tip3") shouldBe Right(())
      repository.getTipHashes() shouldBe Right(List.empty)
    }

    "removing a non-existent tip hash should succeed without error" in {
      repository.addTipHash("tip1") shouldBe Right(())
      repository.removeTipHash("non-existent-tip") shouldBe Right(())
      repository.getTipHashes().map(_.toSet) shouldBe Right(Set("tip1"))
    }

    // --- Test Closing ---
    "close the database without throwing exceptions" in {
      noException should be thrownBy {
         repository.close()
         // Set repository to null to avoid double close in afterEach
         repository = null
      }
    }
  }
}