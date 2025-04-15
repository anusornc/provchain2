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
import scala.util.Try
import scala.util.control.NonFatal
import scala.annotation.tailrec

class BlockValidationServiceImpl(
  override val hashingService: HashingService,
  override val signatureService: SignatureService
) extends BlockValidationService {

  private val canonicalJsonPrinter: Printer = Printer.noSpaces.copy(sortKeys = true)
  private lazy val emptyMerkleRoot: String = hashingService.hashString("").getOrElse("EMPTY_HASH_ERROR_FALLBACK")

  override def validateBlock(block: Block): Either[List[ValidationError], Unit] = {
    val errors = ListBuffer.empty[ValidationError]

    validateSignature(block).foreach(errors += _)
    validateBlockHash(block).foreach(errors += _)
    validateMerkleRoot(block).foreach(errors += _) // ใช้ validateMerkleRoot ที่แก้ไขแล้ว
    validateTimestamp(block).foreach(errors += _)
    errors ++= validateBlockTransactions(block)

    if (errors.isEmpty) {
      Right(())
    } else {
      Left(errors.toList)
    }
  }

  // --- Helper Methods for Validation Steps ---
  // validateSignature, validateBlockHash, validateTimestamp เหมือนเดิม
   private def validateSignature(block: Block): Option[ValidationError] = {
    getBytesForSignature(block) match {
      case Right(dataToVerify) =>
        val publicKeyBytesResult = CryptoUtils.hexStringToBytes(block.validator)
        val signatureBytesResult = CryptoUtils.hexStringToBytes(block.signature)

        (publicKeyBytesResult, signatureBytesResult) match {
          case (Right(pubKey), Right(sig)) =>
            if (signatureService.verify(dataToVerify, sig, pubKey)) {
              None
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
              None
            } else {
              Some(ValidationError.InvalidBlockHash(calculatedHashHex, block.hash))
            }
          case Left(hashError) =>
            Some(ValidationError.InvalidBlockHash(s"Error calculating hash: ${hashError.message}", block.hash))
        }
      case Left(err) => Some(ValidationError.InvalidBlockHash(s"Could not get data for hashing: ${err.message}", block.hash))
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

  // --- Merkle Root Implementation ---

  /** ตรวจสอบ Merkle Root ที่ให้มากับค่าที่คำนวณได้ (แก้ไข) */
  private def validateMerkleRoot(block: Block): Option[ValidationError] = {
    calculateMerkleRoot(block.transactions) match {
      case Right(calculatedRoot) =>
        if (calculatedRoot == block.merkleRoot) {
          None // ถูกต้อง
        } else {
          Some(ValidationError.InvalidMerkleRoot(calculatedRoot, block.merkleRoot)) // Root ไม่ตรง
        }
      // แก้ไข Case นี้: ส่งต่อ Error เดิมที่เกิดตอนคำนวณออกไป
      case Left(calcError: ValidationError) =>
        Some(calcError)
    }
  }

  // calculateMerkleRoot และ buildMerkleTree เหมือนเดิม
   private def calculateMerkleRoot(transactions: List[Transaction]): Either[ValidationError, String] = {
    if (transactions.isEmpty) {
      Right(emptyMerkleRoot)
    } else {
      val dataToHash: List[String] = transactions.map(_.id)
      val initialHashesEither: Either[ValidationError, List[String]] =
        dataToHash.foldLeft[Either[ValidationError, List[String]]](Right(List.empty)) { (accEither, data) =>
          accEither.flatMap { accList =>
            hashingService.hashString(data) match {
              case Right(hash) => Right(accList :+ hash)
              case Left(err)   => Left(ValidationError.InvalidTransactionFormat(data, s"Hashing failed: ${err.message}"))
            }
          }
        }

      initialHashesEither.flatMap { initialHashes =>
        Try(buildMerkleTree(initialHashes)).toEither.left.map {
          case NonFatal(e: RuntimeException) if e.getMessage.startsWith("Hashing failed during tree build:") =>
             // Extract original error message if possible, otherwise use general message
             ValidationError.InvalidMerkleRoot(s"Calculation failed: ${e.getMessage}", "")
          case NonFatal(e) => ValidationError.InvalidMerkleRoot(s"Error building tree: ${e.getMessage}", "")
        }
      }
    }
  }

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
              // แก้ให้ throw RuntimeException เพื่อให้ Try จับได้ง่ายขึ้น
              case Left(err) => throw new RuntimeException(s"Hashing failed during tree build: ${err.message}")
            }
          }
          .toList
        buildMerkleTree(nextLevelHashes)
    }
  }

  // --- Transaction Validation (ยังเป็น Placeholder) ---
   private def validateBlockTransactions(block: Block): List[ValidationError] = {
     val errors = ListBuffer.empty[ValidationError]
     val txIds = block.transactions.map(_.id)
     if (txIds.distinct.size != txIds.size) {
        errors += ValidationError.InvalidTransactionFormat("Block", "Contains duplicate transaction IDs within the same block")
     }
     // TODO: Add more detailed transaction validation here
     errors.toList
   }

  // --- Canonical Byte Representation (เหมือนเดิม) ---
  private def getBytesForHashing(block: Block): Either[ValidationError, Array[Byte]] = {
    Try {
      val jsonForHashing = Json.obj(
        "prevHashes" -> block.prevHashes.asJson,
        "transactions" -> block.transactions.asJson,
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

  private def getBytesForSignature(block: Block): Either[ValidationError, Array[Byte]] = {
    getBytesForHashing(block)
  }
}