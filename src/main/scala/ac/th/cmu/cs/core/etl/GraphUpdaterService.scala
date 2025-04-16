package ac.th.cmu.cs.core.etl

import ac.th.cmu.cs.core.model.Block
import ac.th.cmu.cs.core.graph.GraphError // ใช้ GraphError ที่เคยสร้างไว้

/**
 * Trait (Interface) สำหรับ Service ที่ทำหน้าที่อัปเดต Knowledge Graph (Neo4j)
 * จากข้อมูล Block ใหม่
 */
trait GraphUpdaterService {

  /**
   * ประมวลผล Block ที่ได้รับมา และนำข้อมูลไปสร้าง/อัปเดต Nodes/Relationships ใน Graph Database
   * @param block Block ที่ต้องการประมวลผล
   * @return Right(()) ถ้าสำเร็จ หรือ Left(GraphError) ถ้ามีข้อผิดพลาดในการอัปเดต Graph
   */
  def updateGraph(block: Block): Either[GraphError, Unit]

}