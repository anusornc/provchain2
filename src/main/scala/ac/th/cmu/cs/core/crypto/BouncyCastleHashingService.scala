package ac.th.cmu.cs.infrastructure.crypto

import ac.th.cmu.cs.core.crypto.{CryptoError, HashingService}
import java.security.{MessageDigest, Security}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scala.util.{Try, Success, Failure}

/**
 * Implementation ของ HashingService โดยใช้ Bouncy Castle Provider ผ่าน JCA (Java Cryptography Architecture)
 * ใช้อัลกอริทึม SHA-256
 */
class BouncyCastleHashingService extends HashingService {

  // เพิ่ม Bouncy Castle Provider เข้าไปใน Security Providers ของ JVM ตอนที่ Object นี้ถูกสร้าง
  // ทำเพียงครั้งเดียว
  Try(Security.addProvider(new BouncyCastleProvider())) match {
    case Failure(exception) =>
      // อาจจะ Log warning หรือ throw error ถ้า provider เพิ่มไม่ได้
      // ในที่นี้แค่ print stack trace ถ้ามีปัญหา (อาจจะไม่ใช่ best practice สำหรับ production)
      System.err.println("Warning: Failed to add BouncyCastleProvider, might already be added or other security issue.")
      // exception.printStackTrace() // Uncomment for debugging if needed
    case Success(_) => // Successfully added or already present
  }

  private val HASH_ALGORITHM = "SHA-256"

  override def hashBytes(bytes: Array[Byte]): Either[CryptoError.HashingError, Array[Byte]] = {
    Try {
      val digest = MessageDigest.getInstance(HASH_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
      digest.digest(bytes)
    } match {
      case Success(hashedBytes) => Right(hashedBytes)
      case Failure(e) =>
        Left(CryptoError.HashingError(s"Failed to hash bytes using $HASH_ALGORITHM: ${e.getMessage}", Some(e)))
    }
  }

  // hashString จะใช้ implementation จาก Trait โดยอัตโนมัติ ซึ่งจะเรียก hashBytes ด้านบน
}

// อาจจะสร้างเป็น Singleton object ถ้าไม่ต้องการ state ภายใน service
object BouncyCastleHashingService extends BouncyCastleHashingService