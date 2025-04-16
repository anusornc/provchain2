package ac.th.cmu.cs.core.graph

/**
 * Sealed trait สำหรับแทนข้อผิดพลาดที่อาจเกิดขึ้นในชั้น Graph Database Interaction
 */
sealed trait GraphError extends Product with Serializable {
  def message: String
  def cause: Option[Throwable] = None
}

object GraphError {
  case class ConnectionError(message: String, override val cause: Option[Throwable] = None) extends GraphError
  case class QueryError(message: String, override val cause: Option[Throwable] = None) extends GraphError
  case class TransactionError(message: String, override val cause: Option[Throwable] = None) extends GraphError
  case class InitializationError(message: String, override val cause: Option[Throwable] = None) extends GraphError
  case class ClosingError(message: String, override val cause: Option[Throwable] = None) extends GraphError
  case class ConfigurationError(message: String) extends GraphError
}