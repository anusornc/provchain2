package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.persistence.{BlockRepository, PersistenceError}
import ac.th.cmu.cs.core.service.{BlockValidationService, ChainError}
import ac.th.cmu.cs.core.validation.ValidationError
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => mockEq}

import java.time.Instant

class ChainServiceImplSpec extends BaseSpec with MockitoSugar {

  // --- Helper Data for Tests ---
  val now: Instant = Instant.now()
  def createTestTx(id: String, time: Instant = now): Transaction =
    Transaction(id, time, s"TYPE_$id", None, None, None, Map("attr" -> id), s"sig_$id", s"pk_$id")

  def createTestBlock(hash: String, height: Long, prev: List[String], txs: List[Transaction], time: Instant = now): Block =
    Block(hash, prev, txs, s"mr_$hash", time, height, s"val_$hash", s"sig_$hash", Some(s"SUPPLY_$hash"), height * 10, Map("meta" -> hash))

  val tx1: Transaction = createTestTx("tx1", now.minusSeconds(10))
  val tx2: Transaction = createTestTx("tx2", now.minusSeconds(5))

  val genesisBlock: Block = createTestBlock("h0", 0L, List.empty, List(createTestTx("tx0")), now.minusSeconds(30))
  val block1: Block = createTestBlock("h1", 1L, List("h0"), List(tx1), now.minusSeconds(20))
  val block2: Block = createTestBlock("h2", 2L, List("h1"), List(tx2), now.minusSeconds(10))
  // --- End Helper Data ---

  // --- Test Setup ---
  trait TestFixture {
    val mockRepo = mock[BlockRepository]
    val mockValidator = mock[BlockValidationService]
    val chainService = new ChainServiceImpl(mockRepo, mockValidator)
  }


  "ChainServiceImpl" when {

    // --- Testing addBlock ---
    "adding a valid block" should {
      "validate, check parents, save, update tips, and return the block" in new TestFixture {
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.getBlockByHash(mockEq("h0"))).thenReturn(Right(Some(genesisBlock)))
        when(mockRepo.saveBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.addTipHash(mockEq("h1"))).thenReturn(Right(()))
        when(mockRepo.removeTipHash(mockEq("h0"))).thenReturn(Right(()))

        val result = chainService.addBlock(block1)
        result shouldBe Right(block1)

        verify(mockValidator, times(1)).validateBlock(mockEq(block1))
        verify(mockRepo, times(1)).getBlockByHash(mockEq("h0"))
        verify(mockRepo, times(1)).saveBlock(mockEq(block1))
        verify(mockRepo, times(1)).addTipHash(mockEq("h1"))
        verify(mockRepo, times(1)).removeTipHash(mockEq("h0"))
      }
    }

    "adding a block that fails validation" should {
      "return BlockValidationError and not save or update tips" in new TestFixture {
        val validationErrors = List(ValidationError.InvalidSignature())
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Left(validationErrors))

        val result = chainService.addBlock(block1)
        result shouldBe Left(ChainError.BlockValidationError(validationErrors))

        verify(mockValidator, times(1)).validateBlock(mockEq(block1))
        verify(mockRepo, never()).getBlockByHash(any[String])
        verify(mockRepo, never()).saveBlock(any[Block])
        verify(mockRepo, never()).addTipHash(any[String])
        verify(mockRepo, never()).removeTipHash(any[String])
      }
    }

    "adding a block with a missing parent" should {
      "return ParentNotFound error and not save or update tips" in new TestFixture {
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.getBlockByHash(mockEq("h0"))).thenReturn(Right(None))

        val result = chainService.addBlock(block1)
        result shouldBe Left(ChainError.ParentNotFound(List("h0")))

        verify(mockValidator, times(1)).validateBlock(mockEq(block1))
        verify(mockRepo, times(1)).getBlockByHash(mockEq("h0"))
        verify(mockRepo, never()).saveBlock(any[Block])
      }
    }

     "adding a block that fails during parent check (repository error)" should {
      "return ParentNotFound error (as currently implemented) and not save" in new TestFixture {
        val repoError = PersistenceError.ReadError("DB connection failed")
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.getBlockByHash(mockEq("h0"))).thenReturn(Left(repoError))

        val result = chainService.addBlock(block1)
        result shouldBe Left(ChainError.ParentNotFound(List("h0")))

        verify(mockValidator, times(1)).validateBlock(mockEq(block1))
        verify(mockRepo, times(1)).getBlockByHash(mockEq("h0"))
        verify(mockRepo, never()).saveBlock(any[Block])
      }
    }

    "adding a valid block that fails to save" should {
      "return PersistenceErrorOccurred and not update tips" in new TestFixture {
        val repoError = PersistenceError.WriteError("Disk full")
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.getBlockByHash(mockEq("h0"))).thenReturn(Right(Some(genesisBlock)))
        when(mockRepo.saveBlock(mockEq(block1))).thenReturn(Left(repoError))

        val result = chainService.addBlock(block1)
        result shouldBe Left(ChainError.PersistenceErrorOccurred(repoError))

        verify(mockRepo, times(1)).saveBlock(mockEq(block1))
        verify(mockRepo, never()).addTipHash(any[String])
        verify(mockRepo, never()).removeTipHash(any[String])
      }
    }

    "adding a valid block that saves but fails to update tips" should {
      // แก้ไขชื่อ test case ให้สื่อความหมายตรงขึ้นเล็กน้อย
      "return PersistenceErrorOccurred when adding tip fails" in new TestFixture {
        val tipError = PersistenceError.WriteError("Failed adding tip") // นี่คือ Error จาก Repo
        when(mockValidator.validateBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.getBlockByHash(mockEq("h0"))).thenReturn(Right(Some(genesisBlock)))
        when(mockRepo.saveBlock(mockEq(block1))).thenReturn(Right(()))
        when(mockRepo.addTipHash(mockEq("h1"))).thenReturn(Left(tipError)) // Mock ให้ addTipHash คืน Left(tipError)

        val result = chainService.addBlock(block1)

        // แก้ไข Assertion: คาดหวัง PersistenceErrorOccurred ที่ห่อ tipError ไว้
        result shouldBe Left(ChainError.PersistenceErrorOccurred(tipError))

        verify(mockRepo, times(1)).addTipHash(mockEq("h1"))
        verify(mockRepo, never()).removeTipHash(any[String])
      }
    }

    // --- Testing findBlock ---
    "finding an existing block" should {
      "return the block from the repository" in new TestFixture {
        when(mockRepo.getBlockByHash(mockEq("h1"))).thenReturn(Right(Some(block1)))
        chainService.findBlock("h1") shouldBe Right(Some(block1))
        verify(mockRepo, times(1)).getBlockByHash("h1")
      }
    }

    "finding a non-existent block" should {
      "return None from the repository" in new TestFixture {
        when(mockRepo.getBlockByHash(mockEq("h_none"))).thenReturn(Right(None))
        chainService.findBlock("h_none") shouldBe Right(None)
        verify(mockRepo, times(1)).getBlockByHash("h_none")
      }
    }

     "finding a block when repository fails" should {
      "return PersistenceErrorOccurred" in new TestFixture {
        val repoError = PersistenceError.ReadError("Connection lost")
        when(mockRepo.getBlockByHash(mockEq("h_err"))).thenReturn(Left(repoError))
        chainService.findBlock("h_err") shouldBe Left(ChainError.PersistenceErrorOccurred(repoError))
        verify(mockRepo, times(1)).getBlockByHash("h_err")
      }
    }
  }
}