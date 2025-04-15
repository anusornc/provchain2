package ac.th.cmu.cs.core.model // หรือ ac.th.cmu.cs.model ตามที่ตกลงกัน

import java.time.Instant // สำหรับเก็บ timestamp ที่แม่นยำ

/**
 * Placeholder สำหรับ Transaction
 * ควรจะกำหนด field ต่างๆ ให้สอดคล้องกับข้อมูล PROV-O ที่ต้องการเก็บจริงๆ
 * เช่น id ของ transaction, timestamp, ข้อมูล PROV-O (อาจเป็น JSON หรือ case class เฉพาะ), ลายเซ็นต์ เป็นต้น
 */
case class Transaction(
  id: String,
  timestamp: Instant,
  // TODO: เพิ่ม fields อื่นๆ ที่จำเป็นสำหรับ Transaction และ PROV-O
  // เช่น: agent: String, activity: String, entity: String, relations: Map[String, String]
  payload: String, // หรืออาจใช้ io.circe.Json หรือ Case Class ที่ซับซ้อนกว่านี้
  signature: String // ควรพิจารณาใช้ Vector[Byte] หรือ Type เฉพาะทาง
)

/**
 * โครงสร้างข้อมูลหลักสำหรับ Block ใน Block-DAG
 * ใช้เป็น Case Class เพื่อให้เป็น Immutable และใช้งานง่าย (มี copy, equals, hashCode ให้)
 *
 * @param hash Hash ของข้อมูลใน Block (เช่น SHA-256) - ควรพิจารณาใช้ `Vector[Byte]` หรือ Type เฉพาะสำหรับ Hash
 * @param prevHashes รายการ Hash ของ Block ก่อนหน้าในโครงสร้าง DAG - ควรพิจารณาใช้ `List[String]` หรือ `List[HashType]`
 * @param transactions รายการ Transaction ที่อยู่ใน Block นี้
 * @param merkleRoot Merkle Root ของ Transactions ใน Block - ควรพิจารณาใช้ `Vector[Byte]` หรือ Type เฉพาะสำหรับ Hash
 * @param timestamp เวลาที่ Block ถูกสร้างขึ้น
 * @param height ความสูงหรือลำดับของ Block ใน DAG (ความหมายอาจต่างกันไปใน DAG แต่ละแบบ)
 * @param validator Public Key ของ Validator ที่สร้างและเซ็น Block นี้ - ควรพิจารณาใช้ `Vector[Byte]` หรือ Type เฉพาะสำหรับ Key
 * @param signature ลายเซ็นต์ดิจิทัลของ Block โดย Validator - ควรพิจารณาใช้ `Vector[Byte]`
 * @param supplyChainType (Optional) ประเภทของ Event ใน Supply Chain ที่ Block นี้เกี่ยวข้อง (เช่น 'FARMING', 'PROCESSING', 'SHIPPING')
 * @param dagWeight น้ำหนักหรือคะแนนสะสมของ Block ใน DAG (อาจใช้ใน Consensus หรือการเลือก Tip)
 * @param metadata ข้อมูลเพิ่มเติมอื่นๆ ที่เกี่ยวข้องกับ Block (อาจใช้ Map หรือ JSON Object)
 */
case class Block(
  hash: String, // พิจารณา: Vector[Byte] หรือ type Hash
  prevHashes: List[String], // พิจารณา: List[Hash]
  transactions: List[Transaction], // ใช้ List ของ Transaction ที่เราจะกำหนดต่อไป
  merkleRoot: String, // พิจารณา: Vector[Byte] หรือ type Hash
  timestamp: Instant, // ใช้ java.time.Instant สำหรับเวลา
  height: Long, // ใช้ Long เผื่อค่าเยอะๆ
  validator: String, // พิจารณา: Vector[Byte] หรือ type PublicKey
  signature: String, // พิจารณา: Vector[Byte]
  supplyChainType: Option[String], // ใช้ Option เผื่อ field นี้ไม่มีค่าได้
  dagWeight: Long,
  metadata: Map[String, String] // หรือ Map[String, io.circe.Json] หรือ Case Class เฉพาะ
)

// Companion Object (สำหรับใส่ Factory Methods, Constants, หรือ Utility Functions ที่เกี่ยวกับ Block)
object Block {
  // ตัวอย่าง: อาจจะมี Type Alias เพื่อความชัดเจน
  // type Hash = String // หรือ Vector[Byte]
  // type PublicKey = String // หรือ Vector[Byte]
  // type Signature = String // หรือ Vector[Byte]

  // ตัวอย่าง: อาจจะมี Method สำหรับสร้าง Block เริ่มต้น (Genesis Block)
  // def genesis(initialTransactions: List[Transaction]): Block = { ... }

  // ตัวอย่าง: อาจจะมี Method สำหรับคำนวณ Hash (แต่ปกติจะแยกไปไว้ใน Service/Util)
  // def calculateHash(blockData: ???): String = { ... }
}