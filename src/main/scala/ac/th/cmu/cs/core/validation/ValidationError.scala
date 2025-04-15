package ac.th.cmu.cs.core.validation // สร้าง package ใหม่สำหรับ Validation

/**
 * Sealed trait สำหรับแทนข้อผิดพลาดที่เกิดจากการ Validate ข้อมูล
 */
sealed trait ValidationError extends Product with Serializable {
  def message: String
}

object ValidationError {
  // --- Block Validation Errors ---
  case class InvalidBlockHash(calculatedHash: String, providedHash: String) extends ValidationError {
    val message = s"Invalid block hash. Calculated: $calculatedHash, Provided: $providedHash"
  }
  case class InvalidSignature(details: String = "") extends ValidationError {
     val message = s"Invalid block signature. $details"
  }
  case class InvalidMerkleRoot(calculatedRoot: String, providedRoot: String) extends ValidationError {
     val message = s"Invalid Merkle root. Calculated: $calculatedRoot, Provided: $providedRoot"
  }
  case class InvalidTimestamp(reason: String) extends ValidationError {
     val message = s"Invalid timestamp: $reason"
  }
  case object EmptyTransactionList extends ValidationError {
    val message = "Block contains no transactions (if this is disallowed by rules)"
  }
  // เพิ่ม Error อื่นๆ สำหรับ Block ได้ตามต้องการ เช่น InvalidHeight, MissingPreviousBlock etc.

  // --- Transaction Validation Errors (อาจจะแยกไฟล์ หรือใช้ร่วมกัน) ---
  case class InvalidTransactionFormat(txId: String, reason: String) extends ValidationError {
    val message = s"Invalid format for transaction $txId: $reason"
  }
   case class InvalidTransactionSignature(txId: String) extends ValidationError {
    val message = s"Invalid signature for transaction $txId"
  }
  // เพิ่ม Error อื่นๆ สำหรับ Transaction ได้ตามต้องการ
}