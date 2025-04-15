package ac.th.cmu.cs.core.service

import ac.th.cmu.cs.core.validation.ValidationError
import ac.th.cmu.cs.core.persistence.PersistenceError

/**
 * Sealed trait สำหรับแทนข้อผิดพลาดที่อาจเกิดขึ้นในชั้น Service หลัก (Chain/DAG Service)
 */
sealed trait ChainError extends Product with Serializable {
  def message: String
}

object ChainError {
  /** ห่อหุ้ม ValidationErrors ที่เกิดจาก BlockValidationService */
  case class BlockValidationError(errors: List[ValidationError]) extends ChainError {
    override val message: String = s"Block validation failed: ${errors.map(_.message).mkString("; ")}"
  }

  /** ห่อหุ้ม PersistenceError ที่เกิดจาก BlockRepository */
  case class PersistenceErrorOccurred(error: PersistenceError) extends ChainError {
    override val message: String = s"Persistence error: ${error.message}"
  }

  /** กรณีที่หา Parent Block ไม่เจอใน DAG */
  case class ParentNotFound(missingHashes: List[String]) extends ChainError {
     override val message: String = s"Parent block(s) not found: ${missingHashes.mkString(", ")}"
  }

  /** Error ทั่วไปอื่นๆ ที่อาจเกิดขึ้น */
  case class InternalError(message: String, cause: Option[Throwable] = None) extends ChainError

}