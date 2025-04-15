package ac.th.cmu.cs.core.crypto

import ac.th.cmu.cs.infrastructure.crypto.CryptoUtils // Import utility object
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
    // เรียกใช้ hashBytes แล้วแปลงผลด้วย CryptoUtils
    hashBytes(input.getBytes(charset)).map(CryptoUtils.bytesToHexString)
  }

}