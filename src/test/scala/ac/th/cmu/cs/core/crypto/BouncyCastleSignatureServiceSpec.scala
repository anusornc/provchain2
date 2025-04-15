package ac.th.cmu.cs.infrastructure.crypto

import ac.th.cmu.cs.BaseSpec
import ac.th.cmu.cs.core.crypto.{CryptoError, SignatureService} // Import trait และ Error type
import java.nio.charset.StandardCharsets
import scala.util.Random // สำหรับสร้างข้อมูลเทส

class BouncyCastleSignatureServiceSpec extends BaseSpec {

  // สร้าง instance ของ Service
  val signatureService: SignatureService = BouncyCastleSignatureService

  // ข้อมูลตัวอย่างสำหรับทดสอบการ Sign/Verify
  val testDataString = "This is the data to be signed for ProvChain testing."
  val testDataBytes: Array[Byte] = testDataString.getBytes(StandardCharsets.UTF_8)

  "BouncyCastleSignatureService" should {

    // --- Testing generateKeyPair ---

    "generate valid Ed25519 key pairs successfully" in {
      val keyPairResult = signatureService.generateKeyPair()

      keyPairResult should be(Symbol("right")) // ตรวจสอบว่าสร้าง Key ได้ ไม่มี Error

      keyPairResult.foreach { case (privateKey, publicKey) =>
        privateKey should not be empty // Private key ไม่ควรว่าง
        publicKey should not be empty  // Public key ไม่ควรว่าง
        privateKey should not equal publicKey // Key ทั้งสองต้องต่างกัน

        // อาจจะเพิ่มการตรวจสอบความยาวของ Key ถ้าทราบค่าที่แน่นอนสำหรับ Ed25519
        // เช่น publicKey.length shouldBe EXPECTED_ED25519_PUBLIC_KEY_LENGTH
        // privateKey.length shouldBe EXPECTED_ED25519_PRIVATE_KEY_LENGTH
      }
    }

    "generate different key pairs on multiple calls" in {
      val keyPairResult1 = signatureService.generateKeyPair()
      val keyPairResult2 = signatureService.generateKeyPair()

      keyPairResult1 should be(Symbol("right"))
      keyPairResult2 should be(Symbol("right"))

      // ใช้ .get เพื่อดึงค่าออกจาก Right (ถ้ามั่นใจว่าสำเร็จ หรือใช้ match/getOrElse)
      val (priv1, pub1) = keyPairResult1.getOrElse(fail("First key pair generation failed"))
      val (priv2, pub2) = keyPairResult2.getOrElse(fail("Second key pair generation failed"))

      priv1 should not equal priv2
      pub1 should not equal pub2
    }

    // --- Testing sign and verify (Happy Path) ---

    "correctly sign data and verify the signature with the correct key" in {
      // 1. Generate keys
      val keyPairResult = signatureService.generateKeyPair()
      keyPairResult should be(Symbol("right"))
      val (privateKey, publicKey) = keyPairResult.getOrElse(fail("Key generation failed"))

      // 2. Sign data
      val signatureResult = signatureService.sign(testDataBytes, privateKey)
      signatureResult should be(Symbol("right")) // ตรวจสอบว่า Sign สำเร็จ
      val signature = signatureResult.getOrElse(fail("Signing failed"))
      signature should not be empty // Signature ไม่ควรว่างเปล่า

      // 3. Verify with correct key and data
      val isValid = signatureService.verify(testDataBytes, signature, publicKey)
      isValid shouldBe true // การ Verify ด้วย Key และ Data ที่ถูกต้อง ต้องผ่าน
    }

    // --- Testing verify (Negative Paths) ---

    "fail verification if the signature is tampered" in {
      val keyPairResult = signatureService.generateKeyPair()
      keyPairResult should be(Symbol("right"))
      val (privateKey, publicKey) = keyPairResult.getOrElse(fail("Key generation failed"))

      val signatureResult = signatureService.sign(testDataBytes, privateKey)
      signatureResult should be(Symbol("right"))
      val originalSignature = signatureResult.getOrElse(fail("Signing failed"))

      // สร้าง Signature ที่ผิดเพี้ยน (เช่น กลับ bit แรก)
      val tamperedSignature = originalSignature.clone()
      if (tamperedSignature.nonEmpty) {
        tamperedSignature(0) = (tamperedSignature(0) ^ 0xFF).toByte // Flip bits of the first byte
      } else {
        // Handle empty signature case if necessary, though signing shouldn't produce empty
        fail("Original signature was empty")
      }

      tamperedSignature should not equal originalSignature // Make sure it's different

      val isValid = signatureService.verify(testDataBytes, tamperedSignature, publicKey)
      isValid shouldBe false // Verify ด้วย Signature ที่ผิด ต้องไม่ผ่าน
    }

    "fail verification if the data is tampered" in {
      val keyPairResult = signatureService.generateKeyPair()
      keyPairResult should be(Symbol("right"))
      val (privateKey, publicKey) = keyPairResult.getOrElse(fail("Key generation failed"))

      val signatureResult = signatureService.sign(testDataBytes, privateKey)
      signatureResult should be(Symbol("right"))
      val signature = signatureResult.getOrElse(fail("Signing failed"))

      // สร้าง Data ที่ผิดเพี้ยน
      val tamperedData = (testDataString + " extra stuff").getBytes(StandardCharsets.UTF_8)

      val isValid = signatureService.verify(tamperedData, signature, publicKey)
      isValid shouldBe false // Verify ด้วย Data ที่ผิด ต้องไม่ผ่าน
    }

    "fail verification if the wrong public key is used" in {
      // Generate key pair 1 (used for signing)
      val keyPairResult1 = signatureService.generateKeyPair()
      keyPairResult1 should be(Symbol("right"))
      val (privateKey1, _) = keyPairResult1.getOrElse(fail("Key generation 1 failed"))

      // Generate key pair 2 (used for verification)
      val keyPairResult2 = signatureService.generateKeyPair()
      keyPairResult2 should be(Symbol("right"))
      val (_, publicKey2) = keyPairResult2.getOrElse(fail("Key generation 2 failed"))

      // Sign with private key 1
      val signatureResult = signatureService.sign(testDataBytes, privateKey1)
      signatureResult should be(Symbol("right"))
      val signature = signatureResult.getOrElse(fail("Signing failed"))

      // Verify with public key 2
      val isValid = signatureService.verify(testDataBytes, signature, publicKey2)
      isValid shouldBe false // Verify ด้วย Public Key ที่ผิด ต้องไม่ผ่าน
    }

    // --- Testing sign (Error Path - Optional) ---
    "return SigningError if signing with invalid private key bytes" in {
       val invalidPrivateKey = Random.nextBytes(32) // Random bytes are unlikely a valid key
       val result = signatureService.sign(testDataBytes, invalidPrivateKey)

       result should be(Symbol("left"))
       result.left.foreach { error =>
         error shouldBe a[CryptoError.SigningError]
         // อาจจะเช็ค message เพิ่มเติม ถ้าต้องการความแม่นยำ
         // error.message should include ("key spec") // ขึ้นอยู่กับ Exception ที่ JCA โยนมา
       }
    }

     // --- Testing verify (Error Path - Optional) ---
    "return false if verifying with invalid public key bytes" in {
      // Assume sign was successful
      val keyPairResult = signatureService.generateKeyPair()
      keyPairResult should be(Symbol("right"))
      val (privateKey, _) = keyPairResult.getOrElse(fail("Key generation failed"))
      val signatureResult = signatureService.sign(testDataBytes, privateKey)
      signatureResult should be(Symbol("right"))
      val signature = signatureResult.getOrElse(fail("Signing failed"))

      val invalidPublicKey = Random.nextBytes(32) // Random bytes are unlikely a valid key
      val isValid = signatureService.verify(testDataBytes, signature, invalidPublicKey)

      isValid shouldBe false // Implementation ปัจจุบันคืน false เมื่อเกิด Exception
    }

     "return false if verifying with invalid signature bytes format" in {
      val keyPairResult = signatureService.generateKeyPair()
      keyPairResult should be(Symbol("right"))
      val (_, publicKey) = keyPairResult.getOrElse(fail("Key generation failed"))

      val invalidSignature = Random.nextBytes(10) // Signature Ed25519 ปกติยาว 64 bytes
      val isValid = signatureService.verify(testDataBytes, invalidSignature, publicKey)

      isValid shouldBe false // Implementation ปัจจุบันคืน false เมื่อเกิด Exception
    }
  }
}