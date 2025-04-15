package ac.th.cmu.cs.core.service // สร้าง package ใหม่สำหรับ Core Services

import ac.th.cmu.cs.core.model.Block
import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.crypto.{HashingService, SignatureService} // Import dependencies

/**
 * Trait (Interface) สำหรับ Service ที่ทำหน้าที่ตรวจสอบความถูกต้องของ Block
 */
trait BlockValidationService {

  // --- Dependencies ---
  // Service นี้ต้องใช้ Hashing และ Signature Service
  // เราประกาศเป็น abstract val เพื่อให้ Implementation ต้องกำหนดค่ามาให้ (Dependency Injection)
  val hashingService: HashingService
  val signatureService: SignatureService

  /**
   * ตรวจสอบความถูกต้องของ Block ตามกฏของ Blockchain
   * @param block Block ที่ต้องการตรวจสอบ
   * @return Right(()) ถ้า Block ถูกต้อง หรือ Left(List[ValidationError]) ถ้ามีข้อผิดพลาดอย่างน้อยหนึ่งอย่าง
   */
  def validateBlock(block: Block): Either[List[ValidationError], Unit]

  // อาจจะมี method validate อื่นๆ เช่น validate Header อย่างเดียว หรือ validate Transactions อย่างเดียว
  // def validateBlockHeader(block: Block): Either[List[ValidationError], Unit]
  // def validateTransactions(block: Block): Either[List[ValidationError], Unit]
}