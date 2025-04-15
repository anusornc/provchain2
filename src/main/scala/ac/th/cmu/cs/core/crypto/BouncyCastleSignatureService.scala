package ac.th.cmu.cs.infrastructure.crypto

import ac.th.cmu.cs.core.crypto.{CryptoError, SignatureService}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.{ Signature => JavaSecuritySignature, _ }
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec, X509EncodedKeySpec}
import scala.util.{Failure, Success, Try}

class BouncyCastleSignatureService extends SignatureService {

  Try(Security.addProvider(new BouncyCastleProvider())) match {
    case Failure(exception) =>
      System.err.println("Warning: Failed to add BouncyCastleProvider, might already be added or other security issue.")
    case Success(_) => // OK
  }

  private val KEY_ALGORITHM = "Ed25519"
  private val PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME

  override def generateKeyPair(): Either[CryptoError.KeyGenerationError, (PrivateKey, PublicKey)] = {
    Try {
      val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, PROVIDER_NAME)
      val keyPair = keyPairGenerator.generateKeyPair()
      val privateKeyBytes = keyPair.getPrivate.getEncoded
      val publicKeyBytes = keyPair.getPublic.getEncoded
      (privateKeyBytes, publicKeyBytes)
    } match {
      case Success(keys) => Right(keys)
      case Failure(e) =>
        Left(CryptoError.KeyGenerationError(s"Failed to generate Ed25519 key pair: ${e.getMessage}", Some(e)))
    }
  }

  override def sign(data: Array[Byte], privateKeyBytes: PrivateKey): Either[CryptoError.SigningError, Signature] = {
    Try {
      val keySpec = new PKCS8EncodedKeySpec(privateKeyBytes)
      val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER_NAME)
      val privateKey: java.security.PrivateKey = keyFactory.generatePrivate(keySpec)
      val signatureInstance = JavaSecuritySignature.getInstance(KEY_ALGORITHM, PROVIDER_NAME)
      signatureInstance.initSign(privateKey)
      signatureInstance.update(data)
      val signedBytes = signatureInstance.sign()
      signedBytes
    } match {
      case Success(signatureOutput) => Right(signatureOutput)
      case Failure(e @ (_: NoSuchAlgorithmException | _: NoSuchProviderException | _: InvalidKeyException | _: SignatureException | _: InvalidKeySpecException)) =>
        Left(CryptoError.SigningError(s"Failed to sign data using Ed25519: ${e.getMessage}", Some(e)))
      case Failure(e) => // แก้ไขตรงนี้
         Left(CryptoError.SigningError(s"Unknown error during signing: ${e.getMessage}", Some(e)))
    }
  }

  override def verify(data: Array[Byte], signatureBytes: Signature, publicKeyBytes: PublicKey): Boolean = {
    Try {
      val keySpec = new X509EncodedKeySpec(publicKeyBytes)
      val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER_NAME)
      val publicKey: java.security.PublicKey = keyFactory.generatePublic(keySpec)
      val signatureInstance = JavaSecuritySignature.getInstance(KEY_ALGORITHM, PROVIDER_NAME)
      signatureInstance.initVerify(publicKey)
      signatureInstance.update(data)
      val verificationResult = signatureInstance.verify(signatureBytes)
      verificationResult
    } match {
      case Success(isValidResult) => isValidResult
      case Failure(e) =>
        System.err.println(s"Verification failed due to an error: ${e.getMessage}")
        false
    }
  }
}

object BouncyCastleSignatureService extends BouncyCastleSignatureService