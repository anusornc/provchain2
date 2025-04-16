Phase 0: พื้นฐานและการตั้งค่า (Foundation & Setup) - [ส่วนใหญ่เสร็จแล้ว]

[x] ตั้งค่าโปรเจกต์ Scala ด้วย sbt (build.sbt)
[x] สร้างโครงสร้าง Directory มาตรฐาน (src/main, src/test)
[x] กำหนด Dependencies พื้นฐาน (ScalaTest, Circe, Logging, Config, Mockito, BouncyCastle, RocksDB, Neo4j Driver)
[x] ตั้งค่า Configuration (application.conf)
[x] ตั้งค่า Version Control (.gitignore)
[x] ตั้งค่าสภาพแวดล้อมพัฒนา (IDE: IntelliJ/VS Code, JDK, Git)
Phase 1: Core Models & Infrastructure - [เสร็จแล้ว]

[x] สร้าง Core Data Models:
[x] Block.scala
[x] Transaction.scala (พร้อม fields publicKey, signature และโครง PROV-O เบื้องต้น)
[x] Error Types (PersistenceError.scala, CryptoError.scala, ValidationError.scala, ChainError.scala, GraphError.scala)
[x] Implement JSON Serialization/Deserialization (JsonCodecs.scala)
[x] Implement Cryptography Infrastructure:
[x] Interfaces (HashingService.scala, SignatureService.scala)
[x] Utility (CryptoUtils.scala)
[x] Implementations (BouncyCastleHashingService.scala, BouncyCastleSignatureService.scala)
[x] Implement Persistence Infrastructure:
[x] Interface (BlockRepository.scala)
[x] In-Memory Implementation (InMemoryBlockRepository.scala)
[x] RocksDB Implementation (RocksDbBlockRepository.scala, AutoCloseable)
[x] Implement Neo4j Connector Infrastructure (เบื้องต้น):
[x] Interface (Neo4jConnector.scala)
[x] Implementation (Neo4jConnectorImpl.scala)
[x] เขียน Unit Tests สำหรับ Models, Codecs, Crypto, Persistence (InMemory & RocksDB)
Phase 2: Core Logic & Validation - [เสร็จแล้ว]

[x] สร้าง Core Service Interfaces:
[x] BlockValidationService.scala
[x] ChainService.scala
[x] Implement Core Validation Logic (BlockValidationServiceImpl.scala):
[x] Block Signature Validation
[x] Block Hash Validation
[x] Timestamp Validation
[x] Merkle Root Calculation & Validation (ปัจจุบันใช้ Tx ID)
[x] Transaction Validation (Duplicate ID check, Signature check)
[x] Implement Core Chain Logic (ChainServiceImpl.scala):
[x] Logic การ addBlock (Validate -> Check Parent -> Save -> Update Tips (เบื้องต้น))
[x] ตั้งค่า Manual Dependency Injection ใน Main.scala
[x] สร้างและ Add Valid Genesis Block ใน Main.scala (ใช้ RocksDB)
[x] เขียน Unit Tests สำหรับ Core Services (BlockValidationServiceImplSpec, ChainServiceImplSpec) โดยใช้ Mocks
Phase 3: Knowledge Graph & Traceability (ตามแผนที่ตกลงกัน) - [TODO]

[ ] ออกแบบ Graph Schema ใน Neo4j:
[ ] กำหนด Node Labels (เช่น :Block, :Transaction, :Entity, :Activity, :Agent ตาม PROV-O)
[ ] กำหนด Relationship Types (เช่น :CONTAINS_TX, :PREVIOUS_BLOCK, :USED, :WAS_GENERATED_BY, :WAS_ASSOCIATED_WITH)
[ ] กำหนด Properties ของแต่ละ Node/Relationship
[ ] พิจารณา Index ที่จำเป็นสำหรับการ Query
[ ] Implement ETL Service/Logic:
[ ] สร้าง Service (เช่น GraphUpdaterService) ที่รับ Block ที่ถูกเพิ่มสำเร็จ
[ ] เขียน Logic แปลงข้อมูล Block/Transaction/PROV-O ไปเป็น Cypher Query (CREATE/MERGE Nodes & Relationships)
[ ] เรียกใช้ Neo4jConnector เพื่อ Execute Cypher Query นั้นๆ
[ ] ทำให้ ETL ทำงานร่วมกับ ChainService (อาจจะ Trigger หลังจาก blockRepository.saveBlock สำเร็จ)
[ ] Implement Traceability Query Logic:
[ ] สร้าง Method สำหรับ Query เส้นทาง Traceability ที่พบบ่อย (เช่น หาต้นกำเนิดวัตถุดิบ, หาผลิตภัณฑ์ปลายทาง) โดยใช้ Cypher Query ผ่าน Neo4jConnector
[ ] เขียน Tests สำหรับ Knowledge Graph:
[ ] ทดสอบ ETL process (อาจจะต้องมี Neo4j instance สำหรับ Test)
[ ] ทดสอบ Traceability Queries
Phase 4: Networking & Consensus - [TODO]

[ ] Implement P2P message definitions (Protobuf หรืออื่นๆ)
[ ] Implement message sending/receiving ใน Libp2pService
[ ] Implement Peer Discovery (mDNS / Kademlia DHT)
[ ] Implement PoA Consensus Logic (Validator Set, Rotation, Proposal, Voting)
[ ] Implement Block/Transaction Broadcasting & Synchronization
[ ] เขียน Integration Tests สำหรับ Multi-node scenarios
Phase 5: Application Layer & API - [TODO]

[ ] Implement API Layer (HTTP/GraphQL)
[ ] Endpoint สำหรับ Submit Transaction
[ ] Endpoint สำหรับ Query Block/Transaction
[ ] Endpoint สำหรับ Query Traceability (ผ่าน KG)
[ ] (Optional) Develop User Interfaces
Phase 6: Refinement, Performance & Compliance - [TODO]

[ ] Refactor Merkle Root ให้ใช้ Transaction Hash
[ ] Refactor/Improve Tip Management Logic
[ ] เพิ่ม Validation Rules ที่ละเอียดขึ้น
[ ] Benchmarking & Performance Optimization
[ ] Stress Testing
[ ] Compliance Checks
[ ] Final Documentation / Paper