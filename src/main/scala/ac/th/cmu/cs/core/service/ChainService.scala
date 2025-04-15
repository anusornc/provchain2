package ac.th.cmu.cs.core.service

import ac.th.cmu.cs.core.model.Block
import ac.th.cmu.cs.core.persistence.BlockRepository // Dependency: Repository

/**
 * Trait (Interface) สำหรับ Service หลักที่จัดการ Chain/DAG
 */
trait ChainService {

  // --- Dependencies ---
  val blockRepository: BlockRepository
  val validationService: BlockValidationService

  // --- Core Methods ---

  /**
   * พยายามเพิ่ม Block ใหม่เข้าไปใน Chain/DAG
   * โดยจะทำการ Validate ก่อน แล้วค่อย Save ถ้าผ่าน
   * @param block Block ที่ต้องการเพิ่ม
   * @return Right(Block) ที่เพิ่มสำเร็จ หรือ Left(ChainError) ถ้ามีข้อผิดพลาด
   */
  def addBlock(block: Block): Either[ChainError, Block]

  /**
   * ดึงข้อมูล Block จาก Hash (อาจเป็นแค่ shortcut ไปยัง Repository)
   * @param hash Hash ของ Block ที่ต้องการ
   * @return Right(Some(Block)) / Right(None) หรือ Left(ChainError)
   */
  def findBlock(hash: String): Either[ChainError, Option[Block]]

  // --- อาจจะมี Methods อื่นๆ ---
  // def getChainHeight(): Either[ChainError, Long]
  // def getTips(): Either[ChainError, List[Block]]
}