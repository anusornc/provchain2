package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.service.BlockValidationService
import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.crypto.{HashingService, SignatureService}
import ac.th.cmu.cs.infrastructure.crypto.CryptoUtils
// Import implicit codecs from the central location
import ac.th.cmu.cs.infrastructure.serialization.JsonCodecs._

// Import Circe stuff for JSON manipulation
import io.circe.{Json, Printer}
import io.circe.syntax._

import scala.collection.mutable.ListBuffer
import java.time.Instant
import java.nio.charset.StandardCharsets
import scala.util.Try // Import Try for error handling
import scala.util.control.NonFatal // Import NonFatal for specific exception catching
import scala.annotation.tailrec // Import for tail recursion optimization

/**
 * Implementation ของ BlockValidationService
 * @param hashingService Instance ของ HashingService
 * @param signatureService Instance ของ SignatureService
 */
class BlockValidationServiceImpl(
  override val hashingService: HashingService,
  override val signatureService: SignatureService
) extends BlockValidationService {

  // Printer สำหรับสร้าง JSON string โดยเรียง Key เสมอ และไม่มี Space/Newline
  private val canonicalJsonPrinter: Printer = Printer.noSpaces.copy(sortKeys = true)
  // ค่าคงที่สำหรับ Merkle Root ของ List ว่าง (คำนวณจาก Hash ของ String ว่าง)
  private lazy val emptyMerkleRoot: String = hashingService.hashString("").getOrElse("EMPTY_HASH_ERROR_FALLBACK")

  /**
   * ตรวจสอบความถูกต้องของ Block ทั้งหมด
   */
  override def validateBlock(block: Block): Either[List[ValidationError], Unit] = {
    val errors = ListBuffer.empty[ValidationError]

    validateSignature(block).foreach(errors += _)       // ตรวจ Signature ของ Block
    validateBlockHash(block).foreach(errors += _)         // ตรวจ Hash ของ Block
    validateMerkleRoot(block).foreach(errors += _)      // ตรวจ Merkle Root (ใช้เวอร์ชั่น ID)
    validateTimestamp(block).foreach(errors += _)       // ตรวจ Timestamp
    errors ++= validateBlockTransactions(block)    // ตรวจ Transactions ใน Block

    // --- เพิ่มการตรวจสอบอื่นๆ ตามกฏของ Blockchain ---

    if (errors.isEmpty) {
      Right(()) // ไม่มี Error = ถูกต้อง
    } else {
      Left(errors.toList) // มี Error อย่างน้อย 1 อย่าง
    }
  }

  // --- Helper Methods for Validation Steps ---

  /** ตรวจสอบ Signature ของ Block */
  private def validateSignature(block: Block): Option[ValidationError] = {
    getBytesForSignature(block) match {
      case Right(dataToVerify) =>
        val publicKeyBytesResult = CryptoUtils.hexStringToBytes(block.validator)
        val signatureBytesResult = CryptoUtils.hexStringToBytes(block.signature)

        (publicKeyBytesResult, signatureBytesResult) match {
          case (Right(pubKey), Right(sig)) =>
            if (signatureService.verify(dataToVerify, sig, pubKey)) {
              None // Signature ถูกต้อง
            } else {
              Some(ValidationError.InvalidSignature())
            }
          case (Left(err), _) => Some(ValidationError.InvalidSignature(s"Invalid public key format: ${err.message}"))
          case (_, Left(err)) => Some(ValidationError.InvalidSignature(s"Invalid signature format: ${err.message}"))
        }
      case Left(err) => Some(ValidationError.InvalidSignature(s"Could not get data for signature: ${err.message}"))
    }
  }

  /** ตรวจสอบ Hash ของ Block */
  private def validateBlockHash(block: Block): Option[ValidationError] = {
    getBytesForHashing(block) match {
      case Right(dataToHash) =>
        hashingService.hashBytes(dataToHash) match {
          case Right(calculatedHashBytes) =>
            val calculatedHashHex = CryptoUtils.bytesToHexString(calculatedHashBytes)
            if (calculatedHashHex == block.hash) {
              None // Hash ถูกต้อง
            } else {
              Some(ValidationError.InvalidBlockHash(calculatedHashHex, block.hash))
            }
          case Left(hashError) =>
            Some(ValidationError.InvalidBlockHash(s"Error calculating hash: ${hashError.message}", block.hash))
        }
      case Left(err) => Some(ValidationError.InvalidBlockHash(s"Could not get data for hashing: ${err.message}", block.hash))
    }
  }

  /** ตรวจสอบ Timestamp ของ Block */
   private def validateTimestamp(block: Block): Option[ValidationError] = {
    // กฏพื้นฐาน: Timestamp ไม่ควรเป็นเวลาในอนาคตมากเกินไป (เช่น เกิน 1-2 นาที)
    val maxFutureTime = Instant.now().plusSeconds(120) // อนุญาตให้อยู่ในอนาคตได้ 2 นาที
    if (block.timestamp.isAfter(maxFutureTime)) {
      Some(ValidationError.InvalidTimestamp(s"Block timestamp ${block.timestamp} is too far in the future (current time ${Instant.now()})"))
    } else {
      // อาจจะเพิ่มกฏว่า timestamp ต้องใหม่กว่า block ก่อนหน้า (ต้องใช้ repository)
      None
    }
  }

  // --- Merkle Root Implementation (ใช้ Tx ID) ---

  /** ตรวจสอบ Merkle Root ที่ให้มากับค่าที่คำนวณได้ */
  private def validateMerkleRoot(block: Block): Option[ValidationError] = {
    calculateMerkleRootUsingIds(block.transactions) match { // เรียกเวอร์ชั่นที่ใช้ ID
      case Right(calculatedRoot) =>
        if (calculatedRoot == block.merkleRoot) {
          None // ถูกต้อง
        } else {
          Some(ValidationError.InvalidMerkleRoot(calculatedRoot, block.merkleRoot)) // Root ไม่ตรงกัน
        }
      // กรณีเกิด Error ตอนคำนวณ Merkle Root ให้ส่งต่อ Error นั้น
      case Left(calcError: ValidationError) =>
        Some(calcError)
    }
  }

  /**
   * คำนวณ Merkle Root จากรายการ Transactions (โดย Hash จาก Transaction ID)
   */
   // เปลี่ยนชื่อเป็น UsingIds เพื่อความชัดเจน (จาก Reply ก่อนหน้า)
  private def calculateMerkleRootUsingIds(transactions: List[Transaction]): Either[ValidationError, String] = {
    if (transactions.isEmpty) {
      Right(emptyMerkleRoot) // คืนค่า Root สำหรับ Tree ว่าง
    } else {
      // 1. ดึง Transaction IDs
      val txIds: List[String] = transactions.map(_.id)

      // 2. Hash ID แต่ละตัว (Leaf Hashes)
      val initialHashesEither: Either[ValidationError, List[String]] =
        txIds.foldLeft[Either[ValidationError, List[String]]](Right(List.empty)) { (accEither, txId) =>
          accEither.flatMap { accList =>
            // ใช้ hashString กับ ID
            hashingService.hashString(txId) match {
              case Right(hash) => Right(accList :+ hash)
              // ถ้า Hash ID ไม่สำเร็จ ถือว่าเป็น Invalid Tx Format (อาจจะปรับ Error Type)
              case Left(err)   => Left(ValidationError.InvalidTransactionFormat(txId, s"Hashing ID failed: ${err.message}"))
            }
          }
        }

      // 3. ถ้า Hash ID เริ่มต้นสำเร็จ ให้สร้าง Tree ต่อ
      initialHashesEither.flatMap { initialHashes =>
        Try(buildMerkleTree(initialHashes)).toEither.left.map {
          // จัดการ Error ตอนสร้าง Tree
          case NonFatal(e: RuntimeException) if e.getMessage.startsWith("Hashing failed during tree build:") =>
             ValidationError.InvalidMerkleRoot(s"Calculation failed: ${e.getMessage}", "")
          case NonFatal(e) =>
            val errorMsg = Option(e.getMessage).getOrElse("No message")
            val errorDetails = s"${e.getClass.getName}: $errorMsg"
            ValidationError.InvalidMerkleRoot(s"Error building tree (${errorDetails})", "")
        }
      }
    }
  }

  /**
   * สร้าง Merkle Tree แบบ Recursive (Helper function)
   * รับ List ของ Hex String Hashes และคืนค่า Root Hash เป็น Hex String
   */
  @tailrec
  private def buildMerkleTree(levelHashes: List[String]): String = {
    levelHashes match {
      case Nil => emptyMerkleRoot
      case root :: Nil => root
      case _ =>
        val nextLevelInput = if (levelHashes.length % 2 != 0) levelHashes :+ levelHashes.last else levelHashes
        val nextLevelHashes: List[String] = nextLevelInput
          .grouped(2)
          .map {
            case List(h1, h2) => h1 + h2
            case other => throw new IllegalStateException(s"Unexpected group structure in Merkle tree build: $other")
          }
          .map { combined =>
            hashingService.hashString(combined) match {
              case Right(nextHash) => nextHash
              // โยน Exception ถ้า Hash ไม่สำเร็จ (เพื่อให้ Try ใน calculateMerkleRoot จับได้)
              case Left(err) => throw new RuntimeException(s"Hashing failed during tree build: ${err.message}")
            }
          }
          .toList // รวบรวมผลลัพธ์เป็น List สำหรับ Level ถัดไป
        // เรียก Function ตัวเองซ้ำกับ Hash Level ใหม่
        buildMerkleTree(nextLevelHashes)
    }
  }


  // --- Transaction Validation ---
  /**
   * ตรวจสอบความถูกต้องของ Transaction แต่ละตัวใน Block
   * @param block Block ที่มี Transactions ที่ต้องการตรวจสอบ
   * @return List ของ ValidationErrors ที่พบ (ถ้าไม่มีคือถูกต้อง)
   */
  private def validateBlockTransactions(block: Block): List[ValidationError] = {
    val errors = ListBuffer.empty[ValidationError]

    // 1. ตรวจสอบ Transaction ID ซ้ำซ้อนใน Block เดียวกัน
    val txIds = block.transactions.map(_.id)
    if (txIds.distinct.size != txIds.size) {
      errors += ValidationError.InvalidTransactionFormat("Block", "Contains duplicate transaction IDs within the same block")
    }

    // 2. วนลูปตรวจสอบ Signature ของแต่ละ Transaction
    // ใช้ foldLeft เพื่อรวบรวม Error จากแต่ละ Transaction
    val signatureValidationErrors = block.transactions.foldLeft(ListBuffer.empty[ValidationError]) { (currentErrors, tx) =>
      // ใช้ for-comprehension กับ Either เพื่อจัดการ Error ตอนเตรียมข้อมูล
      val verificationResult: Either[ValidationError, Boolean] = for {
        // 2a. เอาข้อมูล Transaction สำหรับ Verify (ไม่รวม Signature ของ Tx)
        txDataBytes <- Transaction.getBytesForHashing(tx)
                          .left.map(e => ValidationError.InvalidTransactionFormat(tx.id, s"Serialization failed: ${e.getMessage}"))
        // 2b. แปลง Public Key ของ Tx จาก Hex String เป็น Bytes
        txPubKeyBytes <- CryptoUtils.hexStringToBytes(tx.publicKey)
                          .left.map(e => ValidationError.InvalidTransactionFormat(tx.id, s"Invalid public key format: ${e.message}"))
        // 2c. แปลง Signature ของ Tx จาก Hex String เป็น Bytes
        txSigBytes <- CryptoUtils.hexStringToBytes(tx.signature)
                          .left.map(e => ValidationError.InvalidTransactionFormat(tx.id, s"Invalid signature format: ${e.message}"))
      } yield {
        // 2d. ถ้าเตรียมข้อมูลสำเร็จ ให้เรียก signatureService.verify
        signatureService.verify(txDataBytes, txSigBytes, txPubKeyBytes)
      }

      // ตรวจสอบผลลัพธ์ของ Transaction นี้ แล้วเพิ่ม Error ถ้ามี
      verificationResult match {
        case Right(true)  => currentErrors // Signature ถูกต้อง, ไม่เพิ่ม Error
        case Right(false) => currentErrors += ValidationError.InvalidTransactionSignature(tx.id) // Signature ไม่ถูกต้อง
        case Left(error)  => currentErrors += error // เกิด Error ตอนเตรียมข้อมูล (Format ผิด)
      }
    } // จบ foldLeft

    // รวม Error จากการเช็ค ID ซ้ำ และจากการเช็ค Signature
    (errors ++= signatureValidationErrors).toList
  }


  // --- Canonical Byte Representation ---
  // Helper methods to get canonical byte representations using JSON

  /** สร้าง Byte Array สำหรับ Block เพื่อนำไปคำนวณ Hash */
  private def getBytesForHashing(block: Block): Either[ValidationError, Array[Byte]] = {
     Try {
      // สร้าง JSON object โดยเลือก field ที่จะใช้ในการ hash (ไม่รวม hash)
      // ใช้ Merkle Root แทน List ของ Transactions
      val jsonForHashing = Json.obj(
        "prevHashes" -> block.prevHashes.asJson,
        "merkleRoot" -> block.merkleRoot.asJson,
        "timestamp" -> block.timestamp.toEpochMilli.asJson,
        "height" -> block.height.asJson,
        "validator" -> block.validator.asJson,
        "supplyChainType" -> block.supplyChainType.asJson,
        "dagWeight" -> block.dagWeight.asJson,
        "metadata" -> block.metadata.asJson
      )
      canonicalJsonPrinter.print(jsonForHashing).getBytes(StandardCharsets.UTF_8)
    }.toEither.left.map(e => ValidationError.InvalidBlockHash(s"Error creating data for hash: ${e.getMessage}", block.hash))
  }

  /** สร้าง Byte Array สำหรับ Block เพื่อนำไป Verify Signature */
  private def getBytesForSignature(block: Block): Either[ValidationError, Array[Byte]] = {
    // สมมติว่า Signature ครอบคลุมข้อมูลเดียวกับที่ Hash
    getBytesForHashing(block)
  }
} // จบ Class BlockValidationServiceImpl