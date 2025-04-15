package ac.th.cmu.cs.application.service

import ac.th.cmu.cs.core.model.Block
import ac.th.cmu.cs.core.service.{BlockValidationService, ChainError, ChainService}
import ac.th.cmu.cs.core.persistence.BlockRepository
import ac.th.cmu.cs.core.validation.ValidationError

import scala.util.Try
import scala.util.control.NonFatal // Import เพิ่มเติมสำหรับ NonFatal

class ChainServiceImpl(
  override val blockRepository: BlockRepository,
  override val validationService: BlockValidationService
) extends ChainService {

  override def addBlock(block: Block): Either[ChainError, Block] = {
    for {
      _ <- validationService.validateBlock(block).left.map(ChainError.BlockValidationError.apply)
      _ <- checkParentsExist(block)
      _ <- blockRepository.saveBlock(block).left.map(ChainError.PersistenceErrorOccurred.apply)
      _ <- updateTips(block) // ใช้ updateTips ที่แก้ไขแล้ว
    } yield block
  }

  override def findBlock(hash: String): Either[ChainError, Option[Block]] = {
    blockRepository.getBlockByHash(hash).left.map(ChainError.PersistenceErrorOccurred.apply)
  }

  // --- Helper Methods ---

  private def checkParentsExist(block: Block): Either[ChainError, Unit] = {
    if (block.prevHashes.isEmpty && block.height == 0) {
      Right(())
    } else if (block.prevHashes.isEmpty && block.height != 0) {
       Left(ChainError.ParentNotFound(List("Cannot have non-genesis block with empty prevHashes")))
    }
    else {
      val missingParents = block.prevHashes.filter { parentHash =>
        blockRepository.getBlockByHash(parentHash) match {
          case Right(Some(_)) => false
          case Right(None)    => true
          case Left(err)      =>
            println(s"Warning: Error checking parent existence for $parentHash: ${err.message}")
            true
        }
      }

      if (missingParents.isEmpty) {
        Right(())
      } else {
        Left(ChainError.ParentNotFound(missingParents))
      }
    }
  }

  /** อัปเดต Tip Set (แก้ไขให้จัดการ Either จาก addTipHash ถูกต้อง) */
  private def updateTips(addedBlock: Block): Either[ChainError, Unit] = {
    // 1. พยายามเพิ่ม Hash ใหม่เป็น Tip ก่อน และจัดการ Error ถ้าเกิด
    val addResult: Either[ChainError, Unit] =
      blockRepository.addTipHash(addedBlock.hash).left.map(ChainError.PersistenceErrorOccurred.apply)

    // 2. ถ้าการเพิ่มสำเร็จ (เป็น Right) ค่อยไปพยายามลบ Parent ออก (Best effort)
    addResult.flatMap { _ => // ทำงานต่อเมื่อ addResult เป็น Right
      Try { // ใช้ Try ดัก Exception ที่อาจเกิดตอนวนลูปหรือเรียก removeTipHash
        addedBlock.prevHashes.foreach { parentHash =>
          // การเรียก removeTipHash ที่นี่ เราไม่สนใจผลลัพธ์ว่าจะ Left หรือ Right
          // เพราะถือว่าเป็นการพยายามลบเฉยๆ (Idempotent)
          // ถ้าการ remove ล้มเหลวเพราะ Exception จริงๆ Try จะดักจับให้
          blockRepository.removeTipHash(parentHash)
        }
        // ถ้าวนลูปสำเร็จโดยไม่มี Exception ให้ถือว่าขั้นตอนนี้สำเร็จ
        Right(())
      }.toEither.left.map { // แปลง Exception จาก Try เป็น ChainError
        case NonFatal(e) => ChainError.InternalError(s"Exception during parent tip removal: ${e.getMessage}", Some(e))
      }.flatMap(identity) // Flatten nested Either if necessary
    }
  }
}