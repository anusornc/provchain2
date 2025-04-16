package ac.th.cmu.cs.core.graph

import org.neo4j.driver.{AccessMode, Driver, Session} // Import Neo4j Driver classes
import scala.util.Try // ใช้ Try สำหรับจัดการ Exception ตอน get session/close

/**
 * Trait (Interface) สำหรับจัดการการเชื่อมต่อกับ Neo4j Database
 */
trait Neo4jConnector {

  /**
   * ดึง Driver instance ที่จัดการ Connection Pool
   * @return Driver instance
   */
  def getDriver(): Driver // อาจจะคืน Try[Driver] ถ้าการสร้างอาจล้มเหลว

  /**
   * สร้าง Session ใหม่สำหรับโต้ตอบกับ Database
   * @param mode AccessMode (WRITE หรือ READ)
   * @return Try[Session] ซึ่งถ้าสำเร็จจะได้ Session หรือถ้าล้มเหลวจะได้ Exception
   */
  def getSession(mode: AccessMode = AccessMode.WRITE): Try[Session]

  /**
   * ปิด Driver และ Connection Pool ทั้งหมด (ควรเรียกตอน Application Shutdown)
   * @return Try[Unit]
   */
  def closeDriver(): Try[Unit]
}