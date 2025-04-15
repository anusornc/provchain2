package ac.th.cmu.cs.core.crypto

/**
 * Sealed trait สำหรับแทนข้อผิดพลาดที่อาจเกิดขึ้นในชั้น Cryptography
 */
sealed trait CryptoError extends Product with Serializable {
  def message: String
}

object CryptoError {
  case class SigningError(message: String, cause: Option[Throwable] = None) extends CryptoError
  case class VerificationError(message: String) extends CryptoError
  case class KeyGenerationError(message: String, cause: Option[Throwable] = None) extends CryptoError
  case class HashingError(message: String, cause: Option[Throwable] = None) extends CryptoError
  case class InvalidKeyError(message: String) extends CryptoError
  case class UnknownCryptoError(message: String, cause: Option[Throwable] = None) extends CryptoError
}