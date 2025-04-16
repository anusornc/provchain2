package ac.th.cmu.cs.core.model

import java.time.Instant
import java.nio.charset.StandardCharsets
import scala.util.Try
// Import Circe stuff needed for creating JSON manually and the printer
import io.circe.{Json, Printer}
import io.circe.syntax._
// Import implicit codecs defined elsewhere, assuming they are in scope via infrastructure
// Note: This might create a circular dependency if Transaction uses JsonCodecs which uses Transaction.
// A better approach might be to define basic encoders here or pass the printer/encoders explicitly.
// For simplicity now, we assume codecs are available. Consider refactoring later.
import ac.th.cmu.cs.infrastructure.serialization.JsonCodecs._


// --- case class Transaction และอื่นๆ เหมือนเดิม ---
case class Transaction(
  id: String,
  timestamp: Instant,
  transactionType: String,
  provEntity: Option[ProvEntityInfo],
  provActivity: Option[ProvActivityInfo],
  provAgent: Option[ProvAgentInfo],
  attributes: Map[String, String],
  publicKey: String, // Public Key ของผู้สร้าง Tx
  signature: String  // Signature ของ Tx นี้ (จะไม่ถูกรวมตอน Hash Tx)
)

case class ProvEntityInfo(
  id: String,
  entityType: String,
  details: Map[String, String] = Map.empty
)
// ProvActivityInfo, ProvAgentInfo เหมือนเดิม...
case class ProvActivityInfo(
  id: String,
  activityType: String,
  startTime: Option[Instant] = None,
  endTime: Option[Instant] = None,
  location: Option[String] = None
)
case class ProvAgentInfo(
  id: String,
  agentType: String,
  name: Option[String] = None
)
object ProvEntityInfo {}
object ProvActivityInfo {}
object ProvAgentInfo {}


// --- เพิ่ม Companion Object และ Helper Method ---
object Transaction {

  // ใช้ Printer เดียวกับ Block Validation เพื่อความ Consistent
  // Note: การมี Printer อยู่ตรงนี้อาจจะไม่ใช่ design ที่ดีที่สุด อาจจะต้อง Refactor
  private val canonicalJsonPrinter: Printer = Printer.noSpaces.copy(sortKeys = true)

  /**
   * สร้าง Byte Array สำหรับ Transaction เพื่อนำไป Hash (Canonical Representation)
   * จะไม่รวมเอา field 'signature' เข้ามาในการคำนวณ
   * @param tx Transaction ที่ต้องการแปลง
   * @return Right(Array[Byte]) หรือ Left(Throwable) ถ้าการสร้าง JSON ล้มเหลว
   */
  def getBytesForHashing(tx: Transaction): Either[Throwable, Array[Byte]] = {
    Try {
      // สร้าง JSON object โดยเลือก field ที่ต้องการและเรียง Key
      val jsonForHashing = Json.obj(
        "id" -> tx.id.asJson,
        "timestamp" -> tx.timestamp.toEpochMilli.asJson, // ใช้ Epoch Millis เพื่อความแน่นอน
        "transactionType" -> tx.transactionType.asJson,
        "provEntity" -> tx.provEntity.asJson, // ต้องการ Encoder สำหรับ Option[ProvEntityInfo]
        "provActivity" -> tx.provActivity.asJson, // ต้องการ Encoder สำหรับ Option[ProvActivityInfo]
        "provAgent" -> tx.provAgent.asJson, // ต้องการ Encoder สำหรับ Option[ProvAgentInfo]
        "attributes" -> tx.attributes.asJson, // ต้องการ Encoder สำหรับ Map[String, String]
        "publicKey" -> tx.publicKey.asJson
        // ไม่รวม tx.signature
      )
      // แปลงเป็น JSON String แล้วเอา Bytes
      canonicalJsonPrinter.print(jsonForHashing).getBytes(StandardCharsets.UTF_8)
    }.toEither // แปลง Try เป็น Either
  }
}