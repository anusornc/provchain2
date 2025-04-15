// src/main/scala/ac/th/cmu/cs/core/model/Block.scala (ต้องเป็นแบบนี้เท่านั้น)
package ac.th.cmu.cs.core.model

import java.time.Instant
// *** ยืนยันว่าไม่มี case class Transaction หรือ object Transaction ตรงนี้ ***

case class Block(
  hash: String,
  prevHashes: List[String],
  transactions: List[Transaction], // ใช้ Transaction จาก Transaction.scala
  merkleRoot: String,
  timestamp: Instant,
  height: Long,
  validator: String,
  signature: String,
  supplyChainType: Option[String],
  dagWeight: Long,
  metadata: Map[String, String]
)

object Block {
  // Companion object ของ Block
}