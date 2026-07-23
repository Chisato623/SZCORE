package SZCORE

import chisel3._

/** Architectural state held at the boundary between fetch and decode. */
class FetchReg extends Bundle {
  val pc = UInt(32.W)
  val pending = Bool()
  val dropResponse = Bool()
  val responseValid = Bool()
  val responsePc = UInt(32.W)
  val responseInst = UInt(32.W)
}

/** IF/ID pipeline register. */
class IFIDReg extends Bundle {
  val valid = Bool()
  val pc = UInt(32.W)
  val inst = UInt(32.W)
}

/** ID/EX pipeline register. */
class IDEXReg extends Bundle {
  val valid = Bool()
  val pc = UInt(32.W)
  val rs1 = SInt(32.W)
  val rs2 = SInt(32.W)
  val imm = SInt(32.W)
  val rd = UInt(5.W)
  val rs1addr = UInt(5.W)
  val rs2addr = UInt(5.W)
  val funct3 = UInt(3.W)
  val regWrite = Bool()
  val rType = Bool()
  val memLoad = Bool()
  val memStore = Bool()
  val branch = Bool()
  val jal = Bool()
  val jalr = Bool()
  val auipc = Bool()
  val csr = Bool()
  val csrWrite = Bool()
  val csrSet = Bool()
  val csrClear = Bool()
  val csrAddr = UInt(12.W)
  val ecall = Bool()
  val mret = Bool()
  val muldiv = Bool()
  val aluAdd = Bool()
  val aluLh20 = Bool()
  val aluSub = Bool()
  val aluSlt = Bool()
  val aluSltu = Bool()
  val aluXor = Bool()
  val aluOr = Bool()
  val aluAnd = Bool()
  val aluSll = Bool()
  val aluSrl = Bool()
  val aluSra = Bool()
}

/** EX/MEM pipeline register. */
class EXMEMReg extends Bundle {
  val valid = Bool()
  val pc = UInt(32.W)
  val aluResult = SInt(32.W)
  val writebackValue = SInt(32.W)
  val storeData = SInt(32.W)
  val rd = UInt(5.W)
  val funct3 = UInt(3.W)
  val regWrite = Bool()
  val memLoad = Bool()
  val memStore = Bool()
  val csr = Bool()
  val csrWrite = Bool()
  val csrSet = Bool()
  val csrClear = Bool()
  val csrAddr = UInt(12.W)
  val csrOpValue = SInt(32.W)
}

/** MEM/WB pipeline register. */
class MEMWBReg extends Bundle {
  val valid = Bool()
  val pc = UInt(32.W)
  val value = SInt(32.W)
  val rd = UInt(5.W)
  val regWrite = Bool()
  val csr = Bool()
  val csrWrite = Bool()
  val csrSet = Bool()
  val csrClear = Bool()
  val csrAddr = UInt(12.W)
  val csrOpValue = SInt(32.W)
}
