package ac.th.cmu.cs.infrastructure.persistence

import ac.th.cmu.cs.core.model.{Block, Transaction}
import ac.th.cmu.cs.core.persistence.{BlockRepository, PersistenceError}
import ac.th.cmu.cs.infrastructure.serialization.JsonCodecs._
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder, parser, Error => CirceError}
import io.circe.syntax._
import org.rocksdb.{Options, RocksDB, RocksDBException, RocksIterator}
import java.nio.charset.StandardCharsets
import java.io.File
import scala.util.{Try, Success, Failure, Using}
import scala.util.control.NonFatal // <--- Import NonFatal
import scala.collection.mutable.ListBuffer // <--- Import ListBuffer
import java.io.Closeable

class RocksDbBlockRepository(config: Config) extends BlockRepository with Closeable {

  private val BLOCK_PREFIX = "b:"
  private val TX_PREFIX = "t:"
  private val HEIGHT_IDX_PREFIX = "h:"
  private val TIP_HASH_PREFIX = "tip:"
  private val UTF8 = StandardCharsets.UTF_8

  RocksDbBlockRepository.loadLibrary // เรียก lazy val

  private val dbPath: String = config.getString("rocksdb.path")
  private val createIfMissing: Boolean = config.getBoolean("rocksdb.create-if-missing")

  // ใช้ Either สำหรับการเปิด DB
  private val dbEither: Either[PersistenceError, RocksDB] = Try {
    val options = new Options().setCreateIfMissing(createIfMissing)
    val dbDir = new File(dbPath)
    if (!dbDir.exists()) { dbDir.mkdirs() }
    RocksDB.open(options, dbPath)
  }.toEither.left.map {
    case e: RocksDBException => PersistenceError.InitializationError(s"Failed to open RocksDB at '$dbPath': ${e.getStatus.getCodeString}", Some(e))
    case NonFatal(e) => PersistenceError.InitializationError(s"Unexpected error opening RocksDB at '$dbPath'", Some(e))
  }

  // ดึง DB ออกมาใช้งาน หรือ throw ถ้าเปิดไม่สำเร็จ
  private val db: RocksDB = dbEither match {
    case Right(database) =>
       println(s"RocksDB opened successfully at $dbPath")
       database
    // แก้ไข Case นี้: Match กับ InitializationError โดยตรงเพื่อเอา cause
    case Left(error @ PersistenceError.InitializationError(msg, causeOpt)) =>
       println(s"FATAL: RocksDB initialization failed: $msg")
       throw new RuntimeException(s"Could not initialize RocksDB: $msg", causeOpt.orNull)
    case Left(otherError) =>
        println(s"FATAL: RocksDB initialization failed: ${otherError.message}")
        // เข้าถึง cause ถ้ามี (ใช้ pattern match หรือ asInstanceOf)
        val cause = otherError match {
          case PersistenceError.WriteError(_, c) => c.orNull
          case PersistenceError.ReadError(_, c) => c.orNull
          // เพิ่ม case อื่นๆ ถ้าจำเป็น
          case _ => null
        }
        throw new RuntimeException(s"Could not initialize RocksDB: ${otherError.message}", cause)
  }

  // --- Helper Methods ---
  private def blockKey(hash: String): Array[Byte] = (BLOCK_PREFIX + hash).getBytes(UTF8)
  private def txKey(txId: String): Array[Byte] = (TX_PREFIX + txId).getBytes(UTF8)
  private def heightKey(height: Long): Array[Byte] = (HEIGHT_IDX_PREFIX + height.toString).getBytes(UTF8)
  private def tipKey(hash: String): Array[Byte] = (TIP_HASH_PREFIX + hash).getBytes(UTF8)
  private val tipPrefixBytes: Array[Byte] = TIP_HASH_PREFIX.getBytes(UTF8)

  private def serialize[T: Encoder](value: T): Either[PersistenceError, Array[Byte]] = Try(value.asJson.noSpaces.getBytes(UTF8)).toEither.left.map { e => PersistenceError.WriteError(s"JSON Serialization failed: ${e.getMessage}", Some(e)) }
  private def deserialize[T](bytes: Array[Byte])(implicit decoder: Decoder[T]): Either[PersistenceError, T] = Try(new String(bytes, UTF8)).toEither.flatMap(parser.decode[T](_)).left.map { e => PersistenceError.ReadError(s"JSON Deserialization failed: ${e.getMessage}", Some(e)) }

  // --- BlockRepository Implementation ---

  override def saveBlock(block: Block): Either[PersistenceError, Unit] = {
    // 1. เตรียมข้อมูล (Serialize) และจัดการ Error ที่อาจเกิดขึ้น
    val dataPreparationEither: Either[PersistenceError, (Array[Byte], Map[String, Array[Byte]], Array[Byte], Array[Byte])] = for {
      blockBytes <- serialize(block)
      txBytesMap <- block.transactions.foldLeft[Either[PersistenceError, Map[String, Array[Byte]]]](Right(Map.empty)) { (accEither, tx) =>
                      accEither.flatMap(accMap => serialize[ac.th.cmu.cs.core.model.Transaction](tx).map(bytes => accMap + (tx.id -> bytes)))
                    }
      heightKeyBytes = heightKey(block.height)
      // ใช้ Try Option pattern เพื่อให้ปลอดภัยขึ้น
      existingHashesBytesOpt = Try(Option(db.get(heightKeyBytes))).recover { case NonFatal(e) => None }.get // ถ้า Error ตอน get ก็ถือว่าไม่มีค่าเก่า
      currentHashes <- existingHashesBytesOpt.fold[Either[PersistenceError, List[String]]](Right(List.empty))(deserialize[List[String]](_)) // Deserialize ถ้ามีค่าเก่า
      newHashes = (block.hash :: currentHashes).distinct
      newHashesBytes <- serialize(newHashes)
    } yield (blockBytes, txBytesMap, heightKeyBytes, newHashesBytes)

    // 2. ถ้าเตรียมข้อมูลสำเร็จ ให้เขียนลง RocksDB (ใช้ Try จัดการ DB Exception)
    dataPreparationEither.flatMap { case (blockBytes, txBytesMap, heightKeyBytes, newHashesBytes) =>
      Try {
        // เขียนลง DB
        db.put(blockKey(block.hash), blockBytes)
        txBytesMap.foreach { case (id, bytes) => db.put(txKey(id), bytes) }
        db.put(heightKeyBytes, newHashesBytes)
        // สำเร็จ คืน Unit
      }.toEither.left.map { // Map เฉพาะ Exception จาก RocksDB
        case e: RocksDBException => PersistenceError.WriteError(s"RocksDB error during save: ${e.getStatus.getCodeString}", Some(e))
        case NonFatal(e)         => PersistenceError.WriteError(s"Unexpected error during save: ${e.getMessage}", Some(e))
      }
    }
  }

  override def getBlockByHash(hash: String): Either[PersistenceError, Option[Block]] = Try(Option(db.get(blockKey(hash)))).toEither.left.map { e => PersistenceError.ReadError(s"RocksDB error getting block $hash: ${e.getMessage}", Some(e)) }.flatMap { case Some(bytes) => deserialize[Block](bytes).map(Some(_)); case None => Right(None) }
  override def getBlocksByHeight(height: Long): Either[PersistenceError, List[Block]] = Try(Option(db.get(heightKey(height)))).toEither.left.map { e => PersistenceError.ReadError(s"RocksDB error getting height index $height: ${e.getMessage}", Some(e)) }.flatMap {
      case Some(bytes) =>
        deserialize[List[String]](bytes).flatMap { hashes =>
          hashes.foldLeft[Either[PersistenceError, ListBuffer[Block]]](Right(ListBuffer.empty)) { (accEither, h) =>
            accEither.flatMap { accList => getBlockByHash(h).flatMap { case Some(b) => Right(accList += b); case None => Right(accList) } }
          }.map(_.toList)
        }
      case None => Right(List.empty)
    }

  // ใช้ Full Path ของ Transaction
  override def findTransactionById(txId: String): Either[PersistenceError, Option[ac.th.cmu.cs.core.model.Transaction]] = Try(Option(db.get(txKey(txId)))).toEither.left.map { e => PersistenceError.ReadError(s"RocksDB error finding tx $txId: ${e.getMessage}", Some(e)) }.flatMap {
      // ใช้ Full Path ตอน Deserialize
      case Some(bytes) => deserialize[ac.th.cmu.cs.core.model.Transaction](bytes).map(Some(_))
      case None        => Right(None)
    }

  override def getTipHashes(): Either[PersistenceError, List[String]] = Using(db.newIterator()) { iterator => val tipHashes = ListBuffer.empty[String]; iterator.seek(tipPrefixBytes); while (iterator.isValid && iterator.key().startsWith(tipPrefixBytes)) { tipHashes += new String(iterator.key().drop(tipPrefixBytes.length), UTF8); iterator.next() }; tipHashes.toList }.toEither.left.map { e => PersistenceError.ReadError(s"RocksDB error iterating tips: ${e.getMessage}", Some(e)) }

  override def addTipHash(hash: String): Either[PersistenceError, Unit] = {
    // แก้ไข: ใช้ map(_ => ()) แทน void
    Try(db.put(tipKey(hash), Array.emptyByteArray)).toEither.map(_ => ())
      .left.map {
        case e: RocksDBException => PersistenceError.WriteError(s"RocksDB error adding tip $hash: ${e.getStatus.getCodeString}", Some(e))
        case NonFatal(e) => PersistenceError.WriteError(s"Unexpected error adding tip $hash: ${e.getMessage}", Some(e))
      }
  }

  override def removeTipHash(hash: String): Either[PersistenceError, Unit] = {
     // แก้ไข: ใช้ map(_ => ()) แทน void
     Try(db.delete(tipKey(hash))).toEither.map(_ => ())
      .left.map {
        case e: RocksDBException => PersistenceError.WriteError(s"RocksDB error removing tip $hash: ${e.getStatus.getCodeString}", Some(e))
        case NonFatal(e) => PersistenceError.WriteError(s"Unexpected error removing tip $hash: ${e.getMessage}", Some(e))
      }
  }

  // Implement close method from Closeable
  override def close(): Unit = {
     println(s"Closing RocksDB at $dbPath...")
     Try(db.close()).recover {
        case NonFatal(e) => println(s"Error closing RocksDB: ${e.getMessage}")
     }
  }
}

// Companion Object สำหรับโหลด Library ครั้งเดียว
object RocksDbBlockRepository {
    lazy val loadLibrary: Unit = synchronized {
        try {
            RocksDB.loadLibrary()
            println("RocksDB native library loaded successfully.")
        } catch {
            case NonFatal(e) =>
            println(s"FATAL: Failed to load RocksDB native library: ${e.getMessage}")
            e.printStackTrace()
            throw e
        }
    }
}