package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.service.BlockValidationService
import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.crypto.{HashingService, SignatureService}
import ac.th.cmu.cs.infrastructure.crypto.CryptoUtils
import ac.th.cmu.cs.infrastructure.serialization.JsonCodecs._

import io.circe.{Json, Printer}
import io.circe.syntax._

import scala.collection.mutable.ListBuffer
import java.time.Instant
import java.nio.charset.StandardCharsets
import scala.util.Try // <--- เพิ่ม Import Try

class BlockValidationServiceImpl(
  override val hashingService: HashingService,
  override val signatureService: SignatureService
) extends BlockValidationService {

  private val canonicalJsonPrinter: Printer = Printer.noSpaces.copy(sortKeys = true)

  override def validateBlock(block: Block): Either[List[ValidationError], Unit] = {
    val errors = ListBuffer.empty[ValidationError]

    validateSignature(block).foreach(errors += _)
    validateBlockHash(block).foreach(errors += _)
    validateMerkleRoot(block).foreach(errors += _)
    validateTimestamp(block).foreach(errors += _)
    // แก้ไขบรรทัดนี้: ใช้ ++= กับผลลัพธ์ List โดยตรง
    errors ++= validateBlockTransactions(block)

    // --- เพิ่มการตรวจสอบอื่นๆ ---

    if (errors.isEmpty) {
      Right(())
    } else {
      Left(errors.toList)
    }
  }

  // --- Helper Methods for Validation Steps ---

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

   private def validateMerkleRoot(block: Block): Option[ValidationError] = {
    val calculatedRoot = calculateMerkleRoot(block.transactions)
    if (calculatedRoot == block.merkleRoot) {
        None
    } else {
        Some(ValidationError.InvalidMerkleRoot(calculatedRoot, block.merkleRoot))
    }
   }

   private def calculateMerkleRoot(transactions: List[Transaction]): String = {
     if (transactions.isEmpty) {
       hashingService.hashString("").getOrElse("error_hashing_empty")
     } else {
       val combinedData = transactions.map(_.id).sorted.mkString("|") // Sort IDs for consistency
       hashingService.hashString(combinedData).getOrElse(s"error_hashing_tx_${transactions.length}")
     }
   }

  private def validateTimestamp(block: Block): Option[ValidationError] = {
    val maxFutureTime = Instant.now().plusSeconds(120)
    if (block.timestamp.isAfter(maxFutureTime)) {
      Some(ValidationError.InvalidTimestamp(s"Block timestamp ${block.timestamp} is too far in the future (current time ${Instant.now()})"))
    } else {
      None
    }
  }

   private def validateBlockTransactions(block: Block): List[ValidationError] = {
     val errors = ListBuffer.empty[ValidationError]
     val txIds = block.transactions.map(_.id)
     if (txIds.distinct.size != txIds.size) {
        errors += ValidationError.InvalidTransactionFormat("Block", "Contains duplicate transaction IDs within the same block")
     }
     // TODO: Add more detailed transaction validation here
     errors.toList
   }

  // --- Helper methods to get canonical byte representations using JSON ---

  private def getBytesForHashing(block: Block): Either[ValidationError, Array[Byte]] = {
    Try { // ใช้ Try ที่ import มาแล้ว
      val jsonForHashing = Json.obj(
        "prevHashes" -> block.prevHashes.asJson,
        "transactions" -> block.transactions.asJson,
        "merkleRoot" -> block.merkleRoot.asJson,
        "timestamp" -> block.timestamp.toEpochMilli.asJson, // Encode Instant as Long millis for consistency
        "height" -> block.height.asJson,
        "validator" -> block.validator.asJson,
        "supplyChainType" -> block.supplyChainType.asJson,
        "dagWeight" -> block.dagWeight.asJson,
        "metadata" -> block.metadata.asJson
      )
      canonicalJsonPrinter.print(jsonForHashing).getBytes(StandardCharsets.UTF_8)
    }.toEither.left.map(e => ValidationError.InvalidBlockHash(s"Error creating data for hash: ${e.getMessage}", block.hash))
    // หมายเหตุ: แก้ timestamp.asJson เป็น timestamp.toEpochMilli.asJson เพื่อให้ JSON ตรงกันเสมอ
    // เพราะ default Instant encoder อาจมี format ไม่แน่นอน หรือขึ้นกับ TimeZone
  }

  private def getBytesForSignature(block: Block): Either[ValidationError, Array[Byte]] = {
    getBytesForHashing(block)
  }
}