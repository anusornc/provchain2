package ac.th.cmu.cs.core.crypto

/**
 * Trait (Interface) กำหนด Contract สำหรับบริการ Digital Signature (เช่น ED25519)
 */
trait SignatureService {
  // Type Aliases สำหรับ Key และ Signature (ใช้ Array[Byte] ไปก่อน)
  // ในระยะยาว ควรสร้างเป็น Case Class หรือใช้ Type จากไลบรารี Crypto
  type PrivateKey = Array[Byte]
  type PublicKey = Array[Byte]
  type Signature = Array[Byte]

  /**
   * สร้างคู่ Key (Private และ Public) ใหม่
   * @return Tuple ของ (PrivateKey, PublicKey) หรือ Left(KeyGenerationError)
   */
  def generateKeyPair(): Either[CryptoError.KeyGenerationError, (PrivateKey, PublicKey)]

  /**
   * สร้างลายเซ็นดิจิทัลสำหรับข้อมูล
   * @param data ข้อมูลที่ต้องการเซ็น (Array[Byte])
   * @param privateKey Private Key ที่ใช้เซ็น
   * @return Signature (Array[Byte]) หรือ Left(SigningError)
   */
  def sign(data: Array[Byte], privateKey: PrivateKey): Either[CryptoError.SigningError, Signature]

  /**
   * ตรวจสอบความถูกต้องของลายเซ็นดิจิทัล
   * @param data ข้อมูลต้นฉบับที่ถูกเซ็น
   * @param signature ลายเซ็นที่ต้องการตรวจสอบ
   * @param publicKey Public Key ที่สอดคล้องกับ Private Key ที่ใช้เซ็น
   * @return true ถ้าลายเซ็นถูกต้อง, false ถ้าไม่ถูกต้อง (หรืออาจจะ return Left(VerificationError) ด้วย)
   */
  def verify(data: Array[Byte], signature: Signature, publicKey: PublicKey): Boolean // หรือ Either[CryptoError.VerificationError, Boolean]

  // --- Helpers (อาจจะเพิ่ม) ---
  // def publicKeyToString(key: PublicKey): String
  // def stringToPublicKey(s: String): Either[CryptoError.InvalidKeyError, PublicKey]
  // etc.
}