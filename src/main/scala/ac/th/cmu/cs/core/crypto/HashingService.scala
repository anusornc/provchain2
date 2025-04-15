package ac.th.cmu.cs.core.crypto

import java.nio.charset.{Charset, StandardCharsets}

/**
 * Trait (Interface) กำหนด Contract สำหรับบริการ Hashing
 */
trait HashingService {

  /**
   * Hash ข้อมูลที่เป็น Array[Byte]
   * @param bytes ข้อมูลที่ต้องการ Hash
   * @return Array[Byte] ของ Hash (เช่น SHA-256) หรือ Left(HashingError)
   */
  def hashBytes(bytes: Array[Byte]): Either[CryptoError.HashingError, Array[Byte]]

  /**
   * Hash ข้อมูลที่เป็น String (สะดวกใช้)
   * @param input String ที่ต้องการ Hash
   * @param charset Encoding ที่ใช้ (Default: UTF-8)
   * @return String ที่เป็น Hex Representation ของ Hash หรือ Left(HashingError)
   */
  def hashString(input: String, charset: Charset = StandardCharsets.UTF_8): Either[CryptoError.HashingError, String] = {
    hashBytes(input.getBytes(charset)).map(bytesToHexString)
  }

  // --- Helper ---
  // อาจจะย้ายไปไว้ใน Utility Object แยกต่างหาก
  protected def bytesToHexString(bytes: Array[Byte]): String = {
    bytes.map("%02x".format(_)).mkString
  }

  protected def hexStringToBytes(hex: String): Either[IllegalArgumentException, Array[Byte]] = {
    try {
      // Ensure hex string has even length by padding with 0 if necessary
      val paddedHex = if (hex.length % 2 != 0) "0" + hex else hex
      Right(paddedHex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte))
    } catch {
      case e: NumberFormatException => Left(new IllegalArgumentException(s"Invalid hex string: $hex", e))
      case e: Exception => Left(new IllegalArgumentException(s"Error parsing hex string: $hex", e))
    }
  }
}