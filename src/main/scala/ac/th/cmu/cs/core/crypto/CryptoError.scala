package ac.th.cmu.cs.core.crypto

sealed trait CryptoError extends Product with Serializable {
  def message: String
  def cause: Option[Throwable] = None // เพิ่ม default cause เป็น None
}

object CryptoError {
  case class SigningError(message: String, override val cause: Option[Throwable] = None) extends CryptoError
  case class VerificationError(message: String) extends CryptoError // ตัวนี้อาจจะไม่ต้องมี cause ก็ได้
  case class KeyGenerationError(message: String, override val cause: Option[Throwable] = None) extends CryptoError
  case class HashingError(message: String, override val cause: Option[Throwable] = None) extends CryptoError
  // แก้ไข InvalidKeyError ให้รับ cause ได้
  case class InvalidKeyError(message: String, override val cause: Option[Throwable] = None) extends CryptoError // <-- แก้ไขบรรทัดนี้
  case class UnknownCryptoError(message: String, override val cause: Option[Throwable] = None) extends CryptoError
}