package ac.th.cmu.cs.core.model

import java.time.Instant

/**
 * แสดงถึง Transaction หนึ่งรายการในระบบ ProvChain ซึ่งควรจะสอดคล้องกับ PROV-O Model
 *
 * @param id              Unique identifier ของ Transaction นี้
 * @param timestamp       เวลาที่ Transaction ถูกสร้างหรือบันทึก
 * @param transactionType ประเภทของ Transaction (เช่น 'CREATE_BATCH', 'TRANSFER', 'PROCESS')
 * @param provEntity      (Optional) ข้อมูลเกี่ยวกับ PROV-O Entity ที่เกี่ยวข้อง (อาจเป็น ID หรือ Object ซ้อน)
 * @param provActivity    (Optional) ข้อมูลเกี่ยวกับ PROV-O Activity ที่เกี่ยวข้อง (อาจเป็น ID หรือ Object ซ้อน)
 * @param provAgent       (Optional) ข้อมูลเกี่ยวกับ PROV-O Agent ที่เกี่ยวข้อง (อาจเป็น ID หรือ Object ซ้อน)
 * @param attributes      ข้อมูลเพิ่มเติมอื่นๆ ในรูปแบบ Key-Value
 * @param signature       ลายเซ็นดิจิทัลของผู้สร้าง Transaction (ควรใช้ Vector[Byte])
 * @param publicKey       Public Key ของผู้สร้าง Transaction (ควรใช้ Vector[Byte])
 */
case class Transaction(
  id: String,
  timestamp: Instant,
  transactionType: String,
  // --- PROV-O Related Fields (ตัวอย่าง - ต้องปรับตามความต้องการ) ---
  // อาจจะเก็บเป็น ID หรือเป็น Nested Case Class ก็ได้
  provEntity: Option[ProvEntityInfo],
  provActivity: Option[ProvActivityInfo],
  provAgent: Option[ProvAgentInfo],
  // --- End PROV-O ---
  attributes: Map[String, String], // สำหรับข้อมูลอื่นๆ ที่ไม่ตายตัว
  signature: String, // พิจารณา: Vector[Byte] หรือ type Signature
  publicKey: String // พิจารณา: Vector[Byte] หรือ type PublicKey
)

// --- ตัวอย่าง Nested Case Classes สำหรับ PROV-O (อาจแยกไฟล์ได้) ---

/** ข้อมูลเบื้องต้นเกี่ยวกับ PROV-O Entity */
case class ProvEntityInfo(
  id: String,
  entityType: String, // เช่น 'MilkBatch', 'UHTPackage'
  // สามารถเพิ่ม attributes เฉพาะของ Entity ได้
  details: Map[String, String] = Map.empty
)

/** ข้อมูลเบื้องต้นเกี่ยวกับ PROV-O Activity */
case class ProvActivityInfo(
  id: String,
  activityType: String, // เช่น 'Milking', 'Pasteurization', 'Packaging'
  startTime: Option[Instant] = None,
  endTime: Option[Instant] = None,
  location: Option[String] = None
  // สามารถเพิ่ม attributes เฉพาะของ Activity ได้
)

/** ข้อมูลเบื้องต้นเกี่ยวกับ PROV-O Agent */
case class ProvAgentInfo(
  id: String,
  agentType: String, // เช่น 'Farmer', 'Processor', 'Distributor'
  name: Option[String] = None
  // สามารถเพิ่ม attributes เฉพาะของ Agent ได้
)

// Companion Object (ถ้าต้องการ)
object Transaction {
  // อาจจะมี Type Alias หรือ Factory methods
}

// Companion Objects for nested classes (ถ้าต้องการ)
object ProvEntityInfo {}
object ProvActivityInfo {}
object ProvAgentInfo {}