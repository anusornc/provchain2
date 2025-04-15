package ac.th.cmu.cs.infrastructure.crypto

import ac.th.cmu.cs.core.crypto.CryptoError
import scala.util.{Try, Success, Failure}

object CryptoUtils {

  /**
   * แปลง Array[Byte] เป็น Hex String (Lower Case)
   */
  def bytesToHexString(bytes: Array[Byte]): String = {
    // bytes.map(b => String.format("%02x", Byte.box(b))).mkString // Java style boxing
    val sb = new StringBuilder(bytes.length * 2)
    for (b <- bytes) {
      sb.append(String.format("%02x", b))
    }
    sb.toString()
  }

  /**
   * แปลง Hex String เป็น Array[Byte]
   * @return Right(Array[Byte]) หรือ Left(CryptoError.InvalidKeyError) ถ้า String ไม่ใช่ Hex ที่ถูกต้อง
   */
  def hexStringToBytes(hex: String): Either[CryptoError.InvalidKeyError, Array[Byte]] = {
    Try {
      // เอา space ออก, ทำให้เป็นตัวพิมพ์เล็ก, เติม 0 ข้างหน้าถ้าจำเป็น
      val cleanHex = hex.filterNot(_.isWhitespace).toLowerCase()
      val paddedHex = if (cleanHex.length % 2 != 0) "0" + cleanHex else cleanHex
      if (!paddedHex.matches("^[0-9a-f]*$")) {
         throw new IllegalArgumentException("Input contains non-hex characters")
      }
      paddedHex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
    } match {
      case Success(bytes) => Right(bytes)
      case Failure(e: IllegalArgumentException) => Left(CryptoError.InvalidKeyError(s"Invalid hex string format: ${e.getMessage}"))
      case Failure(e) => Left(CryptoError.InvalidKeyError(s"Error parsing hex string '$hex': ${e.getMessage}", Some(e)))
    }
  }
}