package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.service.BlockValidationService
import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.crypto.{CryptoError, HashingService, SignatureService}
import ac.th.cmu.cs.infrastructure.crypto.CryptoUtils
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
// ใช้ Import นี้ให้ถูกต้อง
import org.mockito.ArgumentMatchers.{any => mockitoAny, eq => meq}
import org.scalatest.BeforeAndAfterEach

import java.time.Instant
import java.nio.charset.Charset

class BlockValidationServiceImplSpec extends BaseSpec with MockitoSugar with BeforeAndAfterEach {

  // --- Mock Dependencies ---
  var mockHashingService: HashingService = _
  var mockSignatureService: SignatureService = _
  var validationService: BlockValidationService = _

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

  // --- Service Under Test ---
  override def beforeEach(): Unit = {
    // รีเซ็ต Mock ก่อนทุกครั้ง
    mockHashingService = mock[HashingService](withSettings().defaultAnswer(RETURNS_SMART_NULLS))
    mockSignatureService = mock[SignatureService](withSettings().defaultAnswer(RETURNS_SMART_NULLS))
    validationService = new BlockValidationServiceImpl(mockHashingService, mockSignatureService)

    // Default Stubbing for hashBytes (ให้คืน Empty Hash เสมอ เพื่อง่ายต่อการ Test ส่วนอื่น)
    val defaultHashBytes = CryptoUtils.hexStringToBytes(H_EMPTY).fold(_ => Array.emptyByteArray, identity)
    when(mockHashingService.hashBytes(mockitoAny(classOf[Array[Byte]])))
      .thenReturn(Right(defaultHashBytes))

    // Default Stubbing for signature verify (ให้ผ่านเสมอ)
    when(mockSignatureService.verify(mockitoAny(classOf[Array[Byte]]), mockitoAny(classOf[Array[Byte]]), mockitoAny(classOf[Array[Byte]])))
      .thenReturn(true)

    // Default Stubbing for hashString (ให้คืน Input เดิม เพื่อง่ายต่อการ Test Merkle)
    when(mockHashingService.hashString(mockitoAny(classOf[String]), mockitoAny(classOf[Charset]))).thenAnswer { invocation =>
        val input = invocation.getArgument[String](0)
        if (input == null || input.isEmpty) Right(H_EMPTY) else Right(input)
    }
    // Override สำหรับ empty string โดยเฉพาะ
    when(mockHashingService.hashString(meq(""), mockitoAny(classOf[Charset]))).thenReturn(Right(H_EMPTY))

    super.beforeEach()
  }

  // --- Helper Data ---
  val now: Instant = Instant.now()
  // Helper สร้าง Transaction (ใช้ Hex ที่ Valid)
  def createTx(id: String, pk: String = "aa", sig: String = "bb"): Transaction =
    Transaction(id, now, s"TYPE_$id", None, None, None, Map(), pk, sig)

  // Helper สร้าง Block (ใช้ Hex ที่ Valid)
  val dummyBlockHashHex = H_EMPTY // ใช้ H_EMPTY เป็น default เพื่อให้ตรงกับ Mock hashBytes
  val dummySignatureHex = "cc"
  val dummyValidatorHex = "dd"
  def createBlock(txs: List[Transaction], merkleRoot: String, hash: String = dummyBlockHashHex, sig: String = dummySignatureHex, validator: String = dummyValidatorHex): Block =
    Block(hash, List("prev"), txs, merkleRoot, now, 1L, validator, sig, None, 1L, Map())

  // Helper สำหรับ Mock HashingService.hashString เฉพาะจุด
  def mockHash(input: String, output: String): Unit = {
    when(mockHashingService.hashString(meq(input), mockitoAny(classOf[Charset]))).thenReturn(Right(output))
  }


  "BlockValidationServiceImpl" should {

    // --- Testing Merkle Root Validation (ใช้ Default Mocking จาก beforeEach เป็นหลัก) ---

    "validate Merkle root correctly for an empty transaction list" in {
      val block = createBlock(List(), H_EMPTY) // Merkle Root ถูกต้อง
      mockHash("", H_EMPTY) // Mock hash("")

      val result = validationService.validateBlock(block)
      // คาดหวัง Right หรือ Left ที่ไม่มี Merkle/Block Hash error (เพราะ Mock ให้ผ่าน)
      result.left.foreach(errors => errors.filterNot(_.isInstanceOf[ValidationError.InvalidSignature]) should be (empty))
    }

    "fail validation if Merkle root is incorrect for an empty list" in {
      val block = createBlock(List(), "incorrectRoot")
      mockHash("", H_EMPTY)

      val result = validationService.validateBlock(block)
      result should be (Symbol("left"))
      result.left.foreach(errors => errors should contain (ValidationError.InvalidMerkleRoot(H_EMPTY, "incorrectRoot")))
    }

    "validate Merkle root correctly for a single transaction" in {
      val txs = List(createTx("tx1"))
      // ใน default mock, hash("tx1") จะคืน "tx1"
      val block = createBlock(txs, "tx1")
      mockHash("tx1", "tx1") // Explicit mock (เผื่อ default ไม่ทำงาน)
      mockHash("", H_EMPTY)

      val result = validationService.validateBlock(block)
      result.left.foreach(errors => errors.filterNot(_.isInstanceOf[ValidationError.InvalidSignature]) should not contain a [ValidationError.InvalidMerkleRoot])
    }

     "validate Merkle root correctly for two transactions" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      // ใน default mock, hash("tx1")="tx1", hash("tx2")="tx2", hash("tx1tx2")="tx1tx2"
      val expectedRoot = "tx1tx2"
      val block = createBlock(txs, expectedRoot)
      mockHash("tx1tx2", expectedRoot) // Mock ขั้นสุดท้าย

      val result = validationService.validateBlock(block)
       result.left.foreach(errors => errors.filterNot(_.isInstanceOf[ValidationError.InvalidSignature]) should not contain a [ValidationError.InvalidMerkleRoot])
    }
    // ... (Test Merkle 3, 4 txs สามารถเพิ่มได้ในลักษณะเดียวกัน) ...

    "fail validation if Merkle root is incorrect for multiple transactions" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      val incorrectRoot = "abc"
      val correctRoot = "tx1tx2"
      val block = createBlock(txs, incorrectRoot)
      mockHash("tx1tx2", correctRoot) // Mock การคำนวณที่ถูกต้อง

      val result = validationService.validateBlock(block)
      result should be(Symbol("left"))
      result.left.foreach { errors =>
        errors should contain (ValidationError.InvalidMerkleRoot(correctRoot, incorrectRoot))
      }
    }

     "fail validation if hashing fails during Merkle calculation" in {
      val txs = List(createTx("tx1"), createTx("tx2"))
      val block = createBlock(txs, "dummyMR") // Merkle root ไม่สำคัญในเทสนี้
      val hashError = CryptoError.HashingError("Simulated hash error")

      // ไม่ต้อง mock tx1 เพราะ default คือคืน input
      when(mockHashingService.hashString(meq("tx2"), mockitoAny(classOf[Charset]))).thenReturn(Left(hashError)) // Mock ให้ tx2 hash ไม่ผ่าน

      val result = validationService.validateBlock(block)
      result should be(Symbol("left"))
      result.left.foreach { errors =>
        errors.exists {
          case ValidationError.InvalidTransactionFormat(id, msg) => id == "tx2" && msg.contains("Hashing ID failed")
          case _ => false
        } shouldBe true
      }
    }


    // --- Testing Transaction Validation ---

    "pass validation when block and all transactions have valid signatures" in {
      // ใช้ default mocks จาก beforeEach ซึ่ง mock ให้ hashBytes และ verify ผ่าน
      // สร้าง block ที่มี hash ตรงกับ default mock
      val tx1 = createTx("tx1", pk = "aa", sig = "bb")
      val tx2 = createTx("tx2", pk = "cc", sig = "dd")
      val mr = "tx1tx2" // Merkle คำนวณจาก default hash mock
      mockHash("tx1tx2", mr) // Mock step สุดท้ายของ Merkle ให้ได้ mr ที่ถูกต้อง
      val block = createBlock(List(tx1, tx2), mr, hash = H_EMPTY) // ใช้ hash=H_EMPTY ให้ตรง default mock

      // verify ถูก mock ให้คืน true ใน beforeEach แล้ว

      val result = validationService.validateBlock(block)
      result should be (Right(())) // คาดหวังว่าจะผ่านทั้งหมด
    }

    "fail validation if any transaction has an invalid signature" in {
       val tx1 = createTx("tx1", pk = "aa", sig = "bb") // Valid Sig
       val tx2 = createTx("tx2", pk = "cc", sig = "dd") // Invalid Sig
       val tx3 = createTx("tx3", pk = "ee", sig = "ff") // Valid Sig
       // Merkle คำนวณจาก default mock: h(tx1)=tx1, h(tx2)=tx2, h(tx3)=tx3 ...
       val mr = "tx1tx2tx3tx3"
       mockHash("tx1tx2", "tx1tx2"); mockHash("tx3tx3", "tx3tx3"); mockHash("tx1tx2"+"tx3tx3", mr)
       val block = createBlock(List(tx1, tx2, tx3), mr, hash = H_EMPTY)

       val sigBB = CryptoUtils.hexStringToBytes("bb").fold(_ => Array.emptyByteArray, identity)
       val pkAA = CryptoUtils.hexStringToBytes("aa").fold(_ => Array.emptyByteArray, identity)
       val sigDD = CryptoUtils.hexStringToBytes("dd").fold(_ => Array.emptyByteArray, identity)
       val pkCC = CryptoUtils.hexStringToBytes("cc").fold(_ => Array.emptyByteArray, identity)
       val sigFF = CryptoUtils.hexStringToBytes("ff").fold(_ => Array.emptyByteArray, identity)
       val pkEE = CryptoUtils.hexStringToBytes("ee").fold(_ => Array.emptyByteArray, identity)

       // Override default mock: tx2 verify ไม่ผ่าน
       when(mockSignatureService.verify(mockitoAny(classOf[Array[Byte]]), meq(sigBB), meq(pkAA))).thenReturn(true)
       when(mockSignatureService.verify(mockitoAny(classOf[Array[Byte]]), meq(sigDD), meq(pkCC))).thenReturn(false) // <-- ตัวนี้ไม่ผ่าน
       when(mockSignatureService.verify(mockitoAny(classOf[Array[Byte]]), meq(sigFF), meq(pkEE))).thenReturn(true)

       val result = validationService.validateBlock(block)
       result should be (Symbol("left"))
       result.left.foreach { errors =>
           // ต้องเจอ InvalidTransactionSignature ของ tx2
           errors.exists { case ValidationError.InvalidTransactionSignature("tx2") => true; case _ => false } shouldBe true
           // ไม่ควรเจอของ tx1 หรือ tx3
           errors.exists { case ValidationError.InvalidTransactionSignature("tx1") => true; case _ => false } shouldBe false
           errors.exists { case ValidationError.InvalidTransactionSignature("tx3") => true; case _ => false } shouldBe false
       }
    }

    "fail validation if any transaction has invalid hex format for public key" in {
       val tx1 = createTx("tx1", pk = "aa", sig = "bb")
       val tx2 = createTx("tx2", pk = "invalid-hex", sig = "dd") // <<< PK ผิด Format
       val block = createBlock(List(tx1, tx2), "dummyMR", hash = H_EMPTY)

       // verify tx1 ใช้ default mock (true)

       val result = validationService.validateBlock(block)
       result should be (Symbol("left"))
       result.left.foreach { errors =>
           errors.exists {
               case ValidationError.InvalidTransactionFormat(id, msg) => id == "tx2" && msg.contains("Invalid public key format")
               case _ => false
           } shouldBe true
       }
    }

     "fail validation if any transaction has invalid hex format for signature" in {
      val tx1 = createTx("tx1", pk = "aa", sig = "bb")
      val tx2 = createTx("tx2", pk = "cc", sig = "invalid-sig-hex") // <<< Sig ผิด Format
      val block = createBlock(List(tx1, tx2), "dummyMR", hash = H_EMPTY)

      // verify tx1 ใช้ default mock (true)

      val result = validationService.validateBlock(block)
      result should be (Symbol("left"))
      result.left.foreach { errors =>
           errors.exists {
              case ValidationError.InvalidTransactionFormat(id, msg) => id == "tx2" && msg.contains("Invalid signature format")
              case _ => false
          } shouldBe true
      }
    }

    "fail validation if block contains duplicate transaction IDs" in {
        val tx1 = createTx("tx-dup", pk = "aa", sig = "bb")
        val tx2 = createTx("tx-other", pk = "cc", sig = "dd")
        val tx3 = createTx("tx-dup", pk = "ee", sig = "ff") // <<< ID ซ้ำกับ tx1
        val block = createBlock(List(tx1, tx2, tx3), "dummyMR", hash = H_EMPTY)

        // verify ใช้ default mock (true)

        val result = validationService.validateBlock(block)
        result should be (Symbol("left"))
        result.left.foreach { errors =>
            // ต้องเจอ Error duplicate ID
            errors.exists {
                case ValidationError.InvalidTransactionFormat("Block", msg) if msg.contains("duplicate transaction IDs") => true
                case _ => false
            } shouldBe true
            // ไม่ควรมี Error เรื่อง Signature (เพราะ Tx Signature ควรจะผ่านตาม default mock)
            errors should not contain a [ValidationError.InvalidTransactionSignature]
        }
    }

    "validate transactions within the block" ignore { }

    "return multiple errors if validation fails for multiple reasons" ignore { }
  }
}