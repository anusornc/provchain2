package ac.th.cmu.cs.infrastructure.graph

import ac.th.cmu.cs.core.graph.{GraphError, Neo4jConnector}
import com.typesafe.config.Config // Import Typesafe Config
import org.neo4j.driver._ // Import Neo4j Driver classes
import scala.util.Try

/**
 * Implementation ของ Neo4jConnector โดยใช้ Typesafe Config ในการอ่านค่าเชื่อมต่อ
 *
 * @param config Configuration object (ปกติโหลดจาก application.conf)
 */
class Neo4jConnectorImpl(config: Config) extends Neo4jConnector {

  private val neo4jConfigPath = "neo4j" // Path ใน application.conf

  // สร้าง Driver Instance (ควรเป็น Singleton และสร้างครั้งเดียว)
  // ใช้ lazy val เพื่อให้สร้างเมื่อถูกเรียกใช้ครั้งแรก และจัดการ Exception ตอนสร้าง
  private lazy val driver: Try[Driver] = Try {
    val uri = config.getString(s"$neo4jConfigPath.uri")
    val user = config.getString(s"$neo4jConfigPath.username")
    val password = config.getString(s"$neo4jConfigPath.password")
    // อาจจะเพิ่มการตั้งค่าอื่นๆ เช่น connection pool size จาก config ได้
    // val driverConfig = org.neo4j.driver.Config.builder().build()
    GraphDatabase.driver(uri, AuthTokens.basic(user, password)) // ใช้ Config default
  }.recover {
     // แปลง Exception ตอนสร้าง Driver เป็น GraphError ที่เข้าใจง่ายขึ้น (ถ้าต้องการ)
     case e: Exception => throw GraphError.InitializationError(s"Failed to initialize Neo4j Driver: ${e.getMessage}", Some(e))
  }

  override def getDriver(): Driver = driver match {
    case scala.util.Success(d) => d
    case scala.util.Failure(e) => throw e // โยน Exception เดิมถ้าสร้าง Driver ไม่สำเร็จ
  }

  override def getSession(mode: AccessMode = AccessMode.WRITE): Try[Session] = {
    // ดึง Driver ออกมาจาก Try ก่อน ถ้าสำเร็จค่อยสร้าง Session
    driver.map { d =>
      val sessionConfig = SessionConfig.builder().withDefaultAccessMode(mode).build()
      d.session(sessionConfig)
    }
    // ไม่ต้อง Try ครอบอีกชั้น เพราะถ้า driver เป็น Failure มันจะคืน Failure อยู่แล้ว
  }

  override def closeDriver(): Try[Unit] = {
    // พยายามปิด Driver ถ้ามันเคยถูกสร้างสำเร็จ
    driver.flatMap(d => Try(d.close()))
  }

  // แสดงสถานะตอนสร้าง (เพื่อ Debug)
  driver match {
      case scala.util.Success(_) => println("Neo4j Driver initialized successfully.")
      case scala.util.Failure(e) => println(s"Neo4j Driver initialization failed: ${e.getMessage}")
  }
}