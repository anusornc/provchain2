package ac.th.cmu.cs.infrastructure.crypto

import ac.th.cmu.cs.BaseSpec // Import BaseSpec ที่เราสร้างไว้
import ac.th.cmu.cs.core.crypto.CryptoError
import java.nio.charset.StandardCharsets

class BouncyCastleHashingServiceSpec extends BaseSpec {

  val hashingService = BouncyCastleHashingService

  // -- Known SHA-256 Hashes (Hex String) --
  val emptyInputHashHex: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  val helloHashHex: String = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
  val bytes123HashHex: String = "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81"

  "BouncyCastleHashingService" should {

    // --- Testing hashBytes ---

    "correctly hash an empty byte array" in {
      val input = Array.empty[Byte]
      val result = hashingService.hashBytes(input)
      result should be(Symbol("right"))
      result.foreach { hashedBytes =>
        CryptoUtils.bytesToHexString(hashedBytes) shouldBe emptyInputHashHex
      }
    }

    "correctly hash a known byte array (1, 2, 3)" in {
      val input = Array[Byte](1, 2, 3)
      val result = hashingService.hashBytes(input)
      result should be(Symbol("right"))
      result.foreach { hashedBytes =>
        CryptoUtils.bytesToHexString(hashedBytes) shouldBe bytes123HashHex
      }
    }

    "produce consistent hashes for the same byte array input" in {
      val input = "some consistent data".getBytes(StandardCharsets.UTF_8)
      val result1 = hashingService.hashBytes(input)
      val result2 = hashingService.hashBytes(input)

      result1 should be(Symbol("right"))
      result2 should be(Symbol("right"))
      // แก้ไข: เปรียบเทียบ Seq ข้างใน Either
      result1.map(_.toSeq) shouldBe result2.map(_.toSeq)
    }

    // --- Testing hashString ---

    "correctly hash an empty string" in {
      val input = ""
      val result = hashingService.hashString(input)
      result should be(Symbol("right"))
      result.foreach { hexString =>
        hexString shouldBe emptyInputHashHex
      }
    }

    "correctly hash the string 'hello'" in {
      val input = "hello"
      val result = hashingService.hashString(input)
      result should be(Symbol("right"))
      result.foreach { hexString =>
        hexString shouldBe helloHashHex
      }
    }

    "produce consistent hashes for the same string input" in {
      val input = "another consistent string £$&*("
      val result1 = hashingService.hashString(input)
      val result2 = hashingService.hashString(input)
      result1 should be(Symbol("right"))
      result2 should be(Symbol("right"))
      result1 shouldBe result2
    }

    "produce different hashes for different string inputs" in {
      val input1 = "string 1"
      val input2 = "string 2"
      val result1 = hashingService.hashString(input1)
      val result2 = hashingService.hashString(input2)
      result1 should be(Symbol("right"))
      result2 should be(Symbol("right"))
      result1 shouldNot be(result2)
    }
  }

  // --- Testing CryptoUtils helpers ---
  "CryptoUtils" should {
    "convert bytes to hex string correctly" in {
      CryptoUtils.bytesToHexString(Array[Byte](1, 2, 3)) shouldBe "010203"
      CryptoUtils.bytesToHexString(Array[Byte](-1, 0, 127, -128)) shouldBe "ff007f80"
      CryptoUtils.bytesToHexString(Array.emptyByteArray) shouldBe ""
    }

    "convert hex string to bytes correctly" in {
      // แก้ไข: เปรียบเทียบ Seq ข้างใน Either
      CryptoUtils.hexStringToBytes("010203").map(_.toSeq) shouldBe Right(Array[Byte](1, 2, 3).toSeq)
      CryptoUtils.hexStringToBytes("ff007f80").map(_.toSeq) shouldBe Right(Array[Byte](-1, 0, 127, -128).toSeq)
      CryptoUtils.hexStringToBytes("").map(_.toSeq) shouldBe Right(Array.emptyByteArray.toSeq)
      CryptoUtils.hexStringToBytes("0a").map(_.toSeq) shouldBe Right(Array[Byte](10).toSeq)
      CryptoUtils.hexStringToBytes("a").map(_.toSeq) shouldBe Right(Array[Byte](10).toSeq)
      CryptoUtils.hexStringToBytes(" ").map(_.toSeq) shouldBe Right(Array.emptyByteArray.toSeq)
    }

    "return error for invalid hex string" in {
      val result = CryptoUtils.hexStringToBytes("01020g")
      result should be(Symbol("left"))
      result.left.foreach { error =>
         error shouldBe a[CryptoError.InvalidKeyError]
         error.message should include("Invalid hex string format")
      }

       val result2 = CryptoUtils.hexStringToBytes("01 02 zz")
      result2 should be(Symbol("left"))
      result2.left.foreach { error =>
         error shouldBe a[CryptoError.InvalidKeyError]
         error.message should include("Invalid hex string format")
      }
    }
  }
}