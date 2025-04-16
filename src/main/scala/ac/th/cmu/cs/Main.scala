package ac.th.cmu.cs

// Core Model & Service Interfaces
import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.persistence.BlockRepository
import ac.th.cmu.cs.core.service._
import ac.th.cmu.cs.core.crypto.{CryptoError, HashingService, SignatureService}
import ac.th.cmu.cs.core.validation.ValidationError

// Infrastructure Implementations
import ac.th.cmu.cs.infrastructure.persistence.RocksDbBlockRepository // *** Import RocksDB Repo ***
import ac.th.cmu.cs.infrastructure.crypto.{BouncyCastleHashingService, BouncyCastleSignatureService, CryptoUtils}
import ac.th.cmu.cs.application.service._
import ac.th.cmu.cs.infrastructure.serialization.JsonCodecs._

// Circe for JSON manipulation in Main
import io.circe.{Json, Printer}
import io.circe.syntax._

// Standard Java/Scala imports
import java.time.Instant
import java.nio.charset.StandardCharsets
import com.typesafe.config.ConfigFactory // Import for loading configuration
import scala.util.{Try, Success, Failure}
import java.io.Closeable // Import for AutoCloseable/Closeable check

object Main extends App {

  println("--- Initializing ProvChain Services ---")

  // --- โหลด Configuration ---
  val config = ConfigFactory.load()
  println("Configuration loaded.")

  // --- สร้าง Infrastructure Services ---
  val hashingService: HashingService = BouncyCastleHashingService
  val signatureService: SignatureService = BouncyCastleSignatureService
  println("Crypto Services Initialized.")

  // *** เปลี่ยนมาใช้ RocksDbBlockRepository ***
  val blockRepositoryEither: Either[Throwable, BlockRepository with Closeable] = Try {
      new RocksDbBlockRepository(config) // สร้าง instance ใหม่
  }.toEither // แปลง Try เป็น Either

  // ทำงานต่อเฉพาะเมื่อสร้าง Repository สำเร็จ
  blockRepositoryEither match {
    case Left(initError) =>
      println(s"FATAL: Failed to initialize RocksDbBlockRepository: ${initError.getMessage}")
      initError.printStackTrace()
      println("Exiting due to persistence initialization error.")
      sys.exit(1) // ออกจากโปรแกรมถ้าสร้าง Repo ไม่ได้

    case Right(blockRepository) => // blockRepository is now RocksDbBlockRepository and Closeable
      println(s"Persistence Service (RocksDB at ${config.getString("rocksdb.path")}) Initialized.")

      // --- สร้าง Application Services ---
      val validationService: BlockValidationService = new BlockValidationServiceImpl(hashingService, signatureService)
      println("Validation Service Initialized.")

      val chainService: ChainService = new ChainServiceImpl(blockRepository, validationService)
      println("Chain Service Initialized.")

      // --- เพิ่ม Shutdown Hook สำหรับปิด RocksDB Repository ---
      sys.addShutdownHook {
        println("\n--- Shutting Down ---")
        println("Closing RocksDB Repository...")
        Try(blockRepository.close()) match { // เรียกใช้ close() จาก interface Closeable
          case Success(_) => println("RocksDB Repository closed successfully.")
          case Failure(e) => println(s"Error closing RocksDB Repository: ${e.getMessage}")
        }
        println("Shutdown hook finished.")
      }
      // --- จบ Shutdown Hook ---

      println("--- ProvChain Services Ready ---")

      // --- สร้างและ Add Genesis Block (Logic เหมือนเดิม แต่ใช้ RocksDB แล้ว) ---
      println("\n--- Genesis Block Creation ---")

      // 1. Generate Keys
      val keyPairEither = signatureService.generateKeyPair()
      if (keyPairEither.isLeft) { /* ... sys.exit ... */ }
      val (validatorPrivateKeyBytes, validatorPublicKeyBytes) = keyPairEither.getOrElse(null)
      val validatorPublicKeyHex = CryptoUtils.bytesToHexString(validatorPublicKeyBytes)
      println(s"Generated Genesis Validator Key Pair. Public Key (Hex): $validatorPublicKeyHex")

      // 2. Create and Sign Genesis Transaction
      val genesisTime = Instant.parse("2025-01-01T00:00:00Z")
      val unsignedGenesisTx = Transaction("tx-gen-0", genesisTime, "GENESIS", None, None, None, Map("message" -> "Genesis Transaction"), validatorPublicKeyHex, "")
      val txBytesToSignEither = Transaction.getBytesForHashing(unsignedGenesisTx)
      val txSignatureBytesEither = txBytesToSignEither.flatMap(txBytes => signatureService.sign(txBytes, validatorPrivateKeyBytes))
      val genesisTxEither: Either[Any, Transaction] = txSignatureBytesEither.map { sigBytes => unsignedGenesisTx.copy(signature = CryptoUtils.bytesToHexString(sigBytes)) }
      if (genesisTxEither.isLeft) { /* ... sys.exit ... */ }
      val genesisTx = genesisTxEither.getOrElse(null)
      println(s"Created Signed Genesis Transaction: ${genesisTx.id}")

      // 3. Calculate Merkle Root (from Tx ID)
      val genesisMerkleRootEither: Either[CryptoError.HashingError, String] = hashingService.hashString(genesisTx.id)
      if (genesisMerkleRootEither.isLeft) { /* ... sys.exit ... */ }
      val genesisMerkleRoot = genesisMerkleRootEither.getOrElse("ERROR_MERKLE")
      println(s"Calculated Merkle Root (from Tx ID): $genesisMerkleRoot")

      // 4. Prepare Block Data for Hashing
      val blockHeight = 0L
      val blockTimestamp = genesisTime
      val genesisValidatorPkHex = validatorPublicKeyHex
      val canonicalJsonPrinter: Printer = Printer.noSpaces.copy(sortKeys = true)
      val jsonForBlockHash = Json.obj(
            "prevHashes" -> List.empty[String].asJson,
            "merkleRoot" -> genesisMerkleRoot.asJson,
            "timestamp" -> blockTimestamp.toEpochMilli.asJson,
            "height" -> blockHeight.asJson,
            "validator" -> genesisValidatorPkHex.asJson,
            "supplyChainType" -> Option.empty[String].asJson,
            "dagWeight" -> 0L.asJson,
            "metadata" -> Map("description" -> "The very first block").asJson
          )
      val blockBytesForHashing = canonicalJsonPrinter.print(jsonForBlockHash).getBytes(StandardCharsets.UTF_8)

      // 5. Calculate Block Hash
      val blockHashBytesEither = hashingService.hashBytes(blockBytesForHashing)
      if (blockHashBytesEither.isLeft) { /* ... sys.exit ... */ }
      val blockHashBytes = blockHashBytesEither.getOrElse(Array.emptyByteArray)
      val blockHashHex = CryptoUtils.bytesToHexString(blockHashBytes)
      println(s"Calculated Block Hash: $blockHashHex")

      // 6. Create Block Signature
      val blockSignatureBytesEither = signatureService.sign(blockBytesForHashing, validatorPrivateKeyBytes)
      if (blockSignatureBytesEither.isLeft) { /* ... sys.exit ... */ }
      val blockSignatureBytes = blockSignatureBytesEither.getOrElse(Array.emptyByteArray)
      val blockSignatureHex = CryptoUtils.bytesToHexString(blockSignatureBytes)
      println(s"Created Block Signature.")

      // 7. Construct Final Genesis Block
      val genesisBlock = Block(
          hash = blockHashHex,
          prevHashes = List.empty,
          transactions = List(genesisTx),
          merkleRoot = genesisMerkleRoot,
          timestamp = blockTimestamp,
          height = blockHeight,
          validator = genesisValidatorPkHex,
          signature = blockSignatureHex,
          supplyChainType = None,
          dagWeight = 0L,
          metadata = Map("description" -> "The very first block")
      )
      println(s"Constructed Valid Genesis Block (Hash: ${genesisBlock.hash})")

      // 8. Add Genesis Block
      println("Attempting to add VALID Genesis Block to RocksDB...")
      val addResult = chainService.addBlock(genesisBlock)

      addResult match {
          case Right(addedBlock) =>
            println(s"Successfully added Genesis Block: ${addedBlock.hash}")
            // ลองดึง Block กลับมาดูจาก RocksDB
            chainService.findBlock(addedBlock.hash) match {
              case Right(Some(foundBlock)) => println(s"Successfully retrieved block from RocksDB: ${foundBlock.hash}")
              case Right(None) => println("Error: Could not retrieve the added block from RocksDB!")
              case Left(err) => println(s"Error retrieving block from RocksDB: ${err.message}")
            }
             // ดู Tips จาก RocksDB
             blockRepository.getTipHashes() match {
               case Right(tips) => println(s"Current tips from RocksDB: ${tips.mkString(", ")}")
               case Left(err) => println(s"Error getting tips from RocksDB: ${err.message}")
             }
          case Left(error) =>
            println(s"ERROR: Failed to add VALID Genesis Block to RocksDB!")
            println(s"Reason: ${error.message}")
            error match {
              case ChainError.BlockValidationError(validationErrors) =>
                validationErrors.foreach(ve => println(s" - ${ve.message}"))
              case _ =>
            }
      }
      println("\n--- Example End ---")

  } // จบส่วน Right ของ blockRepositoryEither match
} // จบ Main object