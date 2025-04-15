package ac.th.cmu.cs.core.persistence

import ac.th.cmu.cs.core.model.{Block, Transaction}

/**
 * Trait (Interface) กำหนด Contract สำหรับการจัดการ Block และ Transaction ใน Storage
 * ผลลัพธ์จะอยู่ในรูป `Either[PersistenceError, A]` เพื่อจัดการ Error อย่างชัดเจน
 */
trait BlockRepository {

  /**
   * บันทึก Block ลงใน Storage
   * @param block Block ที่ต้องการบันทึก
   * @return Right(()) ถ้าสำเร็จ หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def saveBlock(block: Block): Either[PersistenceError, Unit]

  /**
   * ดึง Block จาก Storage โดยใช้ Hash
   * @param hash Hash ของ Block ที่ต้องการ
   * @return Right(Some(Block)) ถ้าเจอ, Right(None) ถ้าไม่เจอ, หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def getBlockByHash(hash: String): Either[PersistenceError, Option[Block]]

  /**
   * ดึงรายการ Blocks จาก Storage ตาม Height
   * @param height Height ของ Blocks ที่ต้องการ
   * @return Right(List[Block]) (อาจเป็น List ว่างถ้าไม่เจอ) หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def getBlocksByHeight(height: Long): Either[PersistenceError, List[Block]]

  /**
   * ค้นหา Transaction จาก Storage โดยใช้ ID
   * @param txId ID ของ Transaction ที่ต้องการ
   * @return Right(Some(Transaction)) ถ้าเจอ, Right(None) ถ้าไม่เจอ, หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def findTransactionById(txId: String): Either[PersistenceError, Option[Transaction]]

  /**
   * ดึงรายการ Hash ของ Block ที่เป็น Tip ล่าสุดใน DAG
   * @return Right(List[String]) หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def getTipHashes(): Either[PersistenceError, List[String]]

  /**
   * เพิ่ม Hash เข้าไปในรายการ Tip Hashes (การจัดการ Tip จริงๆ อาจซับซ้อนกว่านี้)
   * @param hash Hash ของ Block ที่เป็น Tip ใหม่
   * @return Right(()) ถ้าสำเร็จ หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def addTipHash(hash: String): Either[PersistenceError, Unit]

   /**
   * ลบ Hash ออกจากรายการ Tip Hashes (การจัดการ Tip จริงๆ อาจซับซ้อนกว่านี้)
   * @param hash Hash ของ Block ที่ไม่ใช่ Tip แล้ว
   * @return Right(()) ถ้าสำเร็จ หรือ Left(PersistenceError) ถ้าผิดพลาด
   */
  def removeTipHash(hash: String): Either[PersistenceError, Unit]

  // อาจจะมี Methods อื่นๆ เพิ่มเติมตามความต้องการ เช่น
  // def findTransactionsByType(txType: String): Either[PersistenceError, List[Transaction]]
  // def countBlocks(): Either[PersistenceError, Long]
  // def initializeStorage(): Either[PersistenceError, Unit] // สำหรับการ Setup ครั้งแรก
}