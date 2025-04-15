package ac.th.cmu.cs.infrastructure.persistence

import ac.th.cmu.cs.core.model.{Block, Transaction}
import ac.th.cmu.cs.core.persistence.{BlockRepository, PersistenceError}
import scala.collection.concurrent.TrieMap // Thread-safe Map

/**
 * Implementation ของ BlockRepository ที่เก็บข้อมูลใน Memory ชั่วคราว
 * เหมาะสำหรับ Development และ Testing เริ่มต้น (ข้อมูลหายเมื่อปิดโปรแกรม)
 * ใช้ TrieMap เพื่อให้ Thread-safe ในระดับพื้นฐาน
 */
class InMemoryBlockRepository extends BlockRepository {

  // ใช้ TrieMap เป็นที่เก็บข้อมูลหลัก (Thread-safe สำหรับ operation พื้นฐาน)
  private val blocksByHash: TrieMap[String, Block] = TrieMap.empty
  private val transactionsById: TrieMap[String, Transaction] = TrieMap.empty
  // เก็บรายการ Hash ของ Block แยกตาม Height เพื่อ Query ได้เร็วขึ้น
  private val blockHashesByHeight: TrieMap[Long, List[String]] = TrieMap.empty
  // ใช้ TrieMap เป็น Set สำหรับเก็บ Tip Hashes
  private val currentTipHashes: TrieMap[String, Unit] = TrieMap.empty

  override def saveBlock(block: Block): Either[PersistenceError, Unit] = {
    try {
      // บันทึก Block หลัก
      blocksByHash.put(block.hash, block)

      // บันทึก Transactions ที่อยู่ใน Block
      block.transactions.foreach { tx =>
        transactionsById.put(tx.id, tx)
      }

      // อัปเดต Index ตาม Height
      // Note: การจัดการ List ใน TrieMap แบบนี้อาจมี Race Condition ถ้ามีการแก้ไขพร้อมกันมากๆ
      // การใช้ update หรือ transform อาจจะปลอดภัยกว่า แต่ซับซ้อนขึ้น
      val currentHashesAtHeight = blockHashesByHeight.getOrElse(block.height, List.empty)
      if (!currentHashesAtHeight.contains(block.hash)) {
        blockHashesByHeight.put(block.height, block.hash :: currentHashesAtHeight)
      }

      Right(()) // คืนค่าสำเร็จ
    } catch {
      case e: Exception => Left(PersistenceError.WriteError(s"Failed to save block ${block.hash}", Some(e)))
    }
  }

  override def getBlockByHash(hash: String): Either[PersistenceError, Option[Block]] = {
    try {
      Right(blocksByHash.get(hash))
    } catch {
      case e: Exception => Left(PersistenceError.ReadError(s"Failed to get block by hash $hash", Some(e)))
    }
  }

  override def getBlocksByHeight(height: Long): Either[PersistenceError, List[Block]] = {
    try {
      // ดึงรายการ Hash จาก Index ก่อน
      val hashes = blockHashesByHeight.getOrElse(height, List.empty)
      // ดึง Block จริงจาก hashes (ใช้ flatMap เพื่อจัดการ Option และกรอง None ออก)
      val blocks = hashes.flatMap(h => blocksByHash.get(h))
      Right(blocks)
    } catch {
      case e: Exception => Left(PersistenceError.ReadError(s"Failed to get blocks by height $height", Some(e)))
    }
  }

  override def findTransactionById(txId: String): Either[PersistenceError, Option[Transaction]] = {
     try {
      Right(transactionsById.get(txId))
    } catch {
      case e: Exception => Left(PersistenceError.ReadError(s"Failed to find transaction by ID $txId", Some(e)))
    }
  }

  override def getTipHashes(): Either[PersistenceError, List[String]] = {
    try {
      Right(currentTipHashes.keys.toList)
    } catch {
      case e: Exception => Left(PersistenceError.ReadError("Failed to get tip hashes", Some(e)))
    }
  }

  override def addTipHash(hash: String): Either[PersistenceError, Unit] = {
    try {
      currentTipHashes.put(hash, ())
      Right(())
    } catch {
      case e: Exception => Left(PersistenceError.WriteError(s"Failed to add tip hash $hash", Some(e)))
    }
  }

  override def removeTipHash(hash: String): Either[PersistenceError, Unit] = {
     try {
      currentTipHashes.remove(hash)
      Right(())
    } catch {
      case e: Exception => Left(PersistenceError.WriteError(s"Failed to remove tip hash $hash", Some(e)))
    }
  }

  // --- Helper methods for testing/debugging (Optional) ---
  def clearAll(): Unit = {
    blocksByHash.clear()
    transactionsById.clear()
    blockHashesByHeight.clear()
    currentTipHashes.clear()
  }
}