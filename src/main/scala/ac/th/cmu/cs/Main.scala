package ac.th.cmu.cs

import ac.th.cmu.cs.core.model._
import ac.th.cmu.cs.core.persistence.BlockRepository
import ac.th.cmu.cs.core.service._
import ac.th.cmu.cs.core.crypto.{HashingService, SignatureService}
import ac.th.cmu.cs.infrastructure.persistence.InMemoryBlockRepository
import ac.th.cmu.cs.infrastructure.crypto.{BouncyCastleHashingService, BouncyCastleSignatureService, CryptoUtils}
import ac.th.cmu.cs.application.service._ // Import implementation classes

import java.time.Instant

object Main extends App { // ใช้ extends App เพื่อให้ run ได้ง่ายๆ

  println("--- Initializing ProvChain Services ---")

  // 1. สร้าง Instances ของ Infrastructure Services (Crypto)
  // เราใช้ Singleton object ที่สร้างไว้
  val hashingService: HashingService = BouncyCastleHashingService
  val signatureService: SignatureService = BouncyCastleSignatureService
  println("Crypto Services Initialized.")

  // 2. สร้าง Instance ของ Infrastructure Services (Persistence)
  // ใช้ InMemory สำหรับตอนนี้
  val blockRepository: BlockRepository = new InMemoryBlockRepository()
  println("Persistence Service (In-Memory) Initialized.")

  // 3. สร้าง Instance ของ Core/Application Services และ Inject Dependencies
  // BlockValidationService ต้องการ HashingService และ SignatureService
  val validationService: BlockValidationService = new BlockValidationServiceImpl(
    hashingService,
    signatureService
  )
  println("Validation Service Initialized.")

  // ChainService ต้องการ BlockRepository และ BlockValidationService
  val chainService: ChainService = new ChainServiceImpl(
    blockRepository,
    validationService
  )
  println("Chain Service Initialized.")

  println("--- ProvChain Services Ready ---")

  // --- ตัวอย่างการใช้งานพื้นฐาน ---
  println("\n--- Basic Usage Example ---")

  // สร้าง Genesis Block (ตัวอย่างง่ายๆ)
  val genesisTime = Instant.parse("2025-01-01T00:00:00Z")
  val genesisTx = Transaction("tx-gen-0", genesisTime, "GENESIS", None, None, None, Map("message" -> "Genesis"), "genesis-sig", "genesis-pk")
  // สมมติว่า Genesis block ไม่มี prev hash, height = 0, และคำนวณ hash/merkle เอง (แบบง่ายๆ)
  val genesisMerkle = hashingService.hashString(genesisTx.id).getOrElse("dummy-merkle") // Merkle แบบง่ายมาก
  val genesisDataForHash = s":$genesisMerkle:${genesisTime.toEpochMilli}:0:genesis-validator::0:".getBytes // ข้อมูลสมมติสำหรับ Hash
  val genesisHashBytes = hashingService.hashBytes(genesisDataForHash).getOrElse(Array.emptyByteArray)
  val genesisHashHex = CryptoUtils.bytesToHexString(genesisHashBytes)

  val genesisBlock = Block(
    hash = genesisHashHex,
    prevHashes = List.empty,
    transactions = List(genesisTx),
    merkleRoot = genesisMerkle,
    timestamp = genesisTime,
    height = 0L,
    validator = "genesis-validator-pk", // ควรเป็น Hex Public Key จริง
    signature = "genesis-block-sig",  // ควรเป็น Hex Signature จริง
    supplyChainType = Some("SYSTEM_INIT"),
    dagWeight = 0L,
    metadata = Map("description" -> "The very first block")
  )
  println(s"Created Genesis Block proposal (Hash: ${genesisBlock.hash})")

  // ลอง Add Genesis Block (ต้องผ่าน Validate ก่อน)
  // หมายเหตุ: การ Validate genesis block จริงๆ อาจมีกฏพิเศษ
  // และในตัวอย่างนี้ validator/signature เป็น dummy ซึ่งจะทำให้ validateSignature ไม่ผ่าน
  // เราจะ comment การ add จริงไว้ก่อน หรือต้อง Mock signature service ถ้าจะทดลองรันส่วนนี้
  println("Attempting to add Genesis Block...")
  val addResult = chainService.addBlock(genesisBlock)

  addResult match {
    case Right(addedBlock) =>
      println(s"Successfully added Genesis Block: ${addedBlock.hash}")
      // ลองดึง Block กลับมาดู
      chainService.findBlock(addedBlock.hash) match {
        case Right(Some(foundBlock)) => println(s"Successfully retrieved block: ${foundBlock.hash}")
        case Right(None) => println("Error: Could not retrieve the added block!")
        case Left(err) => println(s"Error retrieving block: ${err.message}")
      }
      // ดู Tips
       blockRepository.getTipHashes() match {
         case Right(tips) => println(s"Current tips: ${tips.mkString(", ")}")
         case Left(err) => println(s"Error getting tips: ${err.message}")
       }

    case Left(error) =>
      println(s"Failed to add Genesis Block!")
      println(s"Reason: ${error.message}")
      error match {
        case ChainError.BlockValidationError(validationErrors) =>
          validationErrors.foreach(ve => println(s" - ${ve.message}"))
        case _ => // Other chain errors
      }
      println("Note: Validation likely failed due to dummy signature/validator in this example.")
  }

  println("\n--- Example End ---")

}