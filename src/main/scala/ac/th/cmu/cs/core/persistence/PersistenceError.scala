package ac.th.cmu.cs.core.persistence

/**
 * Sealed trait สำหรับแทนข้อผิดพลาดที่อาจเกิดขึ้นในชั้น Persistence
 */
sealed trait PersistenceError extends Product with Serializable {
  def message: String
}

object PersistenceError {
  case class ConnectionError(message: String, cause: Option[Throwable] = None) extends PersistenceError
  case class WriteError(message: String, cause: Option[Throwable] = None) extends PersistenceError
  case class ReadError(message: String, cause: Option[Throwable] = None) extends PersistenceError
  case class NotFoundError(message: String) extends PersistenceError
  case class InitializationError(message: String, cause: Option[Throwable] = None) extends PersistenceError
  case class UnknownError(message: String, cause: Option[Throwable] = None) extends PersistenceError
}