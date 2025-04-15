package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.service.BlockValidationService
import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.crypto.{CryptoError, HashingService, SignatureService}
import ac.th.cmu.cs.infrastructure.crypto.CryptoUtils
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.scalatest.BeforeAndAfterEach // Import เพิ่มเติมสำหรับ beforeEach

import java.time.Instant
import java.nio.charset.Charset

class BlockValidationServiceImplSpec extends BaseSpec with MockitoSugar with BeforeAndAfterEach { // Mixin BeforeAndAfterEach

  // --- Mock Dependencies ---
  // สร้างเป็น var เพื่อให้ reset ใน beforeEach ได้
  var mockHashingService: HashingService = _
  var mockSignatureService: SignatureService = _
  var validationService: BlockValidationService = _

  // --- Service Under Test ---
  // ย้ายการสร้าง Service มาไว้ใน beforeEach เพื่อใช้ mock ที่ reset แล้ว
  override def beforeEach(): Unit = {
    // สร้าง Mock ใหม่ทุกครั้งก่อนรันแต่ละ Test Case
    mockHashingService = mock[HashingService]
    mockSignatureService = mock[SignatureService]
    validationService = new BlockValidationServiceImpl(mockHashingService, mockSignatureService)

    // *** เพิ่ม: Mock hashBytes ให้มีค่า Default ป้องกัน MatchError: null ***
    // ให้คืนค่า Hash ของ Empty String เป็น Default (หรือค่า Dummy อื่น)
    // เราไม่สนใจค่า Hash นี้โดยตรงใน Test Merkle แต่ต้องมีเพื่อให้ match ไม่พัง
    val defaultHashBytes = CryptoUtils.hexStringToBytes(H_EMPTY).getOrElse(Array.emptyByteArray)
    when(mockHashingService.hashBytes(any(classOf[Array[Byte]])))
      .thenReturn(Right(defaultHashBytes))

    // Mock Signature ให้ผ่านเป็น Default ด้วยเลย จะได้ไม่ error เรื่อง key format ใน validateSignature
    when(mockSignatureService.verify(any(), any(), any())).thenReturn(true)

    super.beforeEach() // เรียก super ถ้าจำเป็น
  }


  // --- Helper Data --- (เหมือนเดิม)
  val now: Instant = Instant.now()
  def createTx(id: String): Transaction =
    Transaction(id, now, s"TYPE_$id", None, None, None, Map(), s"sig_$id", s"pk_$id")

  val dummyBlockHashHex = "001122aabbcc" // ใช้ hash dummy ที่เป็น hex ได้
  val dummySignatureHex = "sig112233"
  val dummyValidatorHex = "val445566" // ควรเป็น hex ที่ถูกต้องถ้าจะเทส signature จริงจัง
  def createBlock(txs: List[Transaction], merkleRoot: String, hash: String = dummyBlockHashHex, sig: String = dummySignatureHex, validator: String = dummyValidatorHex): Block =
    Block(hash, List("prev"), txs, merkleRoot, now, 1L, validator, sig, None, 1L, Map())

  // Pre-calculated Hashes (เหมือนเดิม)
  val H_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  val H1      = "1a6dd018a68747e66b623331b4380413c392708160f109a300511e41a4935a01"
  val H2      = "f2e1ecf9ea6606e6f0710571a93ba18e3553489548906705f1515b67b3f14ae4"
  val H3      = "9775036366f616355f044e414f2d5867f93639cf5930bd6a1b8945e0db81c655"
  val H4      = "2168a768273650a7584045970c6139e76f787d15f8180730b566a30cf6847caf"
  val H12     = "29870a06d1ac8f37b1104240e948f5a35c2580b5e6e59b17d557e2791a4d5d58"
  val H33     = "b2106d4e44f060a9a57011601f1a63665f030fb8931e175bb80ce66809d5287a"
  val H34     = "d9418b1d8f2b56f45d036873a7840f368c1f4a3563445f2f9e206b35e1f8441a"
  val H1233   = "a9e73b5b8f6c7a0c6b5d3f0e9e3f4e6d7f0c8a2d3e6b9e7e9b5d8a7c6f3e2d1b"
  val H1234   = "e3225116456d15e64b55e530761768731ab96d3944b1f061317ba0260f0f96d0"


  // Helper สำหรับ Mock HashingService.hashString
  def mockHash(input: String, output: String): Unit = {
    when(mockHashingService.hashString(mockEq(input), any[Charset])).thenReturn(Right(output))
  }

  "BlockValidationServiceImpl" should {

    // --- Testing Merkle Root Validation ---

    "validate Merkle root correctly for an empty transaction list" in {
      // ใช้ validationService ที่สร้างใน beforeEach
      val block = createBlock(List(), H_EMPTY, hash = H_EMPTY) // สมมติ hash block ตรงกับ empty hash ด้วย

      // Mock Hashing สำหรับ Merkle
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach แล้ว

      val result = validationService.validateBlock(block)
      result match {
          case Right(_) => succeed
          // ถ้าเกิด Left, ต้องไม่มี InvalidMerkleRoot และ InvalidBlockHash (เพราะเรา mock hashBytes ให้ตรง)
          case Left(errors) =>
            errors should not contain a [ValidationError.InvalidMerkleRoot]
            errors should not contain a [ValidationError.InvalidBlockHash]
      }
    }

    "fail validation if Merkle root is incorrect for an empty list" in {
      val block = createBlock(List(), "incorrectRoot")

      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
      result should be (Symbol("left"))
      result.left.foreach(errors => errors should contain (ValidationError.InvalidMerkleRoot(H_EMPTY, "incorrectRoot")))
    }

    "validate Merkle root correctly for a single transaction" in {
      val txs = List(createTx("tx1"))
      val block = createBlock(txs, H1, hash = H_EMPTY) // ใช้ hash dummy ไปก่อน

      mockHash("tx1", H1)
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
      result match {
          case Right(_) => succeed
          case Left(errors) => errors should not contain a [ValidationError.InvalidMerkleRoot]
          // อาจจะมี InvalidBlockHash ซึ่งไม่เป็นไร
      }
    }

     "validate Merkle root correctly for two transactions" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      val block = createBlock(txs, H12, hash = H_EMPTY)

      mockHash("tx1", H1)
      mockHash("tx2", H2)
      mockHash(H1 + H2, H12)
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
       result match {
          case Right(_) => succeed
          case Left(errors) => errors should not contain a [ValidationError.InvalidMerkleRoot]
      }
    }

     "validate Merkle root correctly for three transactions (odd number)" in {
      val txs = List(createTx("tx1"), createTx("tx2"), createTx("tx3"))
      val block = createBlock(txs, H1233, hash = H_EMPTY)

      mockHash("tx1", H1)
      mockHash("tx2", H2)
      mockHash("tx3", H3)
      mockHash(H1 + H2, H12)
      mockHash(H3 + H3, H33)
      mockHash(H12 + H33, H1233)
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
       result match {
          case Right(_) => succeed
          case Left(errors) => errors should not contain a [ValidationError.InvalidMerkleRoot]
      }
    }

     "validate Merkle root correctly for four transactions" in {
      val txs = List(createTx("tx1"), createTx("tx2"), createTx("tx3"), createTx("tx4"))
      val block = createBlock(txs, H1234, hash = H_EMPTY)

      mockHash("tx1", H1)
      mockHash("tx2", H2)
      mockHash("tx3", H3)
      mockHash("tx4", H4)
      mockHash(H1 + H2, H12)
      mockHash(H3 + H4, H34)
      mockHash(H12 + H34, H1234)
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
       result match {
          case Right(_) => succeed
          case Left(errors) => errors should not contain a [ValidationError.InvalidMerkleRoot]
      }
    }

    "fail validation if Merkle root is incorrect for multiple transactions" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      val incorrectRoot = "abc"
      val block = createBlock(txs, incorrectRoot, hash = H_EMPTY)

      mockHash("tx1", H1)
      mockHash("tx2", H2)
      mockHash(H1 + H2, H12)
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
      result should be(Symbol("left"))
      result.left.foreach { errors =>
        errors should contain (ValidationError.InvalidMerkleRoot(H12, incorrectRoot))
      }
    }

     "fail validation if hashing fails during Merkle calculation" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      val block = createBlock(txs, H12, hash = H_EMPTY)
      val hashError = CryptoError.HashingError("Simulated hash error")

      mockHash("tx1", H1)
      when(mockHashingService.hashString(mockEq("tx2"), any[Charset])).thenReturn(Left(hashError)) // Mock ให้ tx2 hash ไม่ผ่าน
      mockHash("", H_EMPTY)
      // verify และ hashBytes ถูก Mock ใน beforeEach

      val result = validationService.validateBlock(block)
      result should be(Symbol("left"))
      result.left.foreach { errors =>
        errors should contain (ValidationError.InvalidTransactionFormat("tx2", s"Hashing failed: ${hashError.message}"))
      }
    }

    "validate transactions within the block" ignore {
        // TODO: Test cases for transaction validation logic
    }
  }
}