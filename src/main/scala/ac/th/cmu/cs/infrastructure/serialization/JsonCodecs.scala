package ac.th.cmu.cs.infrastructure.serialization

import ac.th.cmu.cs.core.model._ // Import model classes
import io.circe._
import io.circe.generic.semiauto._

/**
 * กำหนด JSON Encoders และ Decoders สำหรับ Data Models โดยใช้ Circe
 * การใช้ `deriveEncoder` และ `deriveDecoder` จะช่วยสร้าง codec โดยอัตโนมัติสำหรับ case class
 */
object JsonCodecs {

  // --- Codecs for PROV-O Info classes ---
  implicit val provEntityInfoDecoder: Decoder[ProvEntityInfo] = deriveDecoder[ProvEntityInfo]
  implicit val provEntityInfoEncoder: Encoder[ProvEntityInfo] = deriveEncoder[ProvEntityInfo]

  implicit val provActivityInfoDecoder: Decoder[ProvActivityInfo] = deriveDecoder[ProvActivityInfo]
  implicit val provActivityInfoEncoder: Encoder[ProvActivityInfo] = deriveEncoder[ProvActivityInfo]

  implicit val provAgentInfoDecoder: Decoder[ProvAgentInfo] = deriveDecoder[ProvAgentInfo]
  implicit val provAgentInfoEncoder: Encoder[ProvAgentInfo] = deriveEncoder[ProvAgentInfo]

  // --- Codec for Transaction ---
  // ต้องมี implicit codecs ของ field ทุกตัวใน scope (Instant มีแล้วจาก circe-java8)
  implicit val transactionDecoder: Decoder[Transaction] = deriveDecoder[Transaction]
  implicit val transactionEncoder: Encoder[Transaction] = deriveEncoder[Transaction]

  // --- Codec for Block ---
  // ต้องการ implicit codecs ของ Transaction และ Instant
  implicit val blockDecoder: Decoder[Block] = deriveDecoder[Block]
  implicit val blockEncoder: Encoder[Block] = deriveEncoder[Block]

}