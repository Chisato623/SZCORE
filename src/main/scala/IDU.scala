package SZCORE
import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._
import chisel3.util._
class IDU extends Module {
  val io = IO(new Bundle {
    val inst      = Input(UInt(32.W))
    val pc        = Input(UInt(32.W))
    val rs1data   = Input(SInt(32.W))
    val rs2data   = Input(SInt(32.W))
    val lui       = Output(Bool())
    val jalr      = Output(Bool())
    val auipc     = Output(Bool())
    val ecall     = Output(Bool())
    val mret      = Output(Bool())
    val dobranch  = Output(Bool())
    val I         = Output(Bool())
    val S         = Output(Bool())
    val B         = Output(Bool())
    val U         = Output(Bool())
    val J         = Output(Bool())
    val R         = Output(Bool())
    val rs1addr   = Output(UInt(5.W))
    val rs2addr   = Output(UInt(5.W))
    val rdaddr    = Output(UInt(5.W))
    val alu_add   = Output(Bool())
    val alu_lh20  = Output(Bool())
    val alu_sub   = Output(Bool())
    val alu_slt   = Output(Bool())
    val alu_sltu  = Output(Bool())
    val alu_xor   = Output(Bool())
    val alu_or    = Output(Bool())
    val alu_and   = Output(Bool())
    val alu_sll   = Output(Bool())
    val alu_srl   = Output(Bool())
    val alu_sra   = Output(Bool())
    val alu_mul = Output(Bool())
    val alu_mulh = Output(Bool())
    val alu_mulsu = Output(Bool())
    val alu_mulu = Output(Bool())
    val alu_div = Output(Bool())
    val alu_divu = Output(Bool())
    val alu_rem = Output(Bool())
    val alu_remu = Output(Bool())
    val alu_a     = Output(SInt(32.W))
    val alu_b     = Output(SInt(32.W))
    val mem_load  = Output(Bool())
    val csr       = Output(Bool())
    val csr_write = Output(Bool())
    val csr_set   = Output(Bool())
    val csr_clear = Output(Bool())

    val csraddr = Output(UInt(12.W))
  })

  val inst = io.inst
  val pc   = io.pc
  val opcode = inst(6, 0)
  val funct3 = inst(14, 12)
  val funct7 = inst(31, 25)

  val imm11_0 = inst(31, 20) // I-type
  val imm11_5 = inst(31, 25) // S-type
  val imm4_0  = inst(11, 7)  // S-type

  val imm12_b   = inst(31)     // B-type
  val imm10_5_b = inst(30, 25) // B-type
  val imm4_1_b  = inst(11, 8)  // B-type
  val imm11_b   = inst(7)      // B-type

  val imm31_12 = inst(31, 12) // U-type

  val imm20_j    = inst(31)     // J-type
  val imm10_1_j  = inst(30, 21) // J-type
  val imm11_j    = inst(20)     // J-type
  val imm19_12_j = inst(19, 12) // J-type

  val I_imm = Cat(Fill(20, imm11_0(11)), imm11_0).asSInt
  val S_imm = Cat(Fill(20, imm11_5(6)), imm11_5, imm4_0).asSInt
  val B_imm = Cat(Fill(20, imm12_b), imm12_b, imm10_5_b, imm4_1_b, 0.U(1.W)).asSInt
  val U_imm = Cat(imm31_12, 0.U(12.W)).asSInt
  val J_imm = Cat(Fill(11, imm20_j), imm20_j, imm19_12_j, imm11_j, imm10_1_j, 0.U(1.W)).asSInt

  val isLoad   = opcode === "b0000011".U
  val isStore  = opcode === "b0100011".U
  val isBranch = opcode === "b1100011".U
  val isJalr   = opcode === "b1100111".U
  val isJal    = opcode === "b1101111".U
  val isOpImm  = opcode === "b0010011".U
  val isRop    = opcode === "b0110011".U
  val isCsr    = opcode === "b1110011".U
  val isEcall  = (inst === 0x00000073.U)
  val isMret   = (inst === 0x30200073.U)
  // csr op
  val isWrite  = (isCsr && funct3 === "b001".U)
  val isSet    = (isCsr && funct3 === "b010".U)
  val isClear  = (isCsr && funct3 === "b011".U)
//alu op
  val isLui    = opcode === "b0110111".U
  val isAuipc  = opcode === "b0010111".U
  val isAdd    = (isRop && funct3 === "b000".U && funct7 === "b0000000".U) || // ADD
    (isOpImm && funct3 === "b000".U) || // ADDI
    isLoad ||                           // LOAD
    isStore ||                          // STORE
    isJalr ||                           // JALR
    isBranch ||                         // BRANCH
    isAuipc ||                          // AUIPC
    isJal                               // JAL
  val isSub  = (isRop && funct3 === "b000".U && funct7 === "b0100000".U)
  val isSlt  = ((isOpImm || isRop) && funct3 === "b010".U)
  val isSltu = ((isOpImm || isRop) && funct3 === "b011".U)
  val isXor  = (funct3 === "b100".U) && (isRop || isOpImm)
  val isOr   = (funct3 === "b110".U) && (isOpImm || isRop)
  val isAnd  = (funct3 === "b111".U) && (isOpImm || isRop)
  val isSll  = (funct3 === "b001".U) && (isOpImm || isRop)
  val isSrl  = (funct7 === "b0000000".U) && (funct3 === "b101".U) && (isOpImm || isRop)
  val isSra  = (funct7 === "b0100000".U) && (funct3 === "b101".U) && (isOpImm || isRop)
  val isMul    = (funct7 === "b0000001".U) && (funct3 === "b000".U) && isRop
  val isMulh   = (funct7 === "b0000001".U) && (funct3 === "b001".U) && isRop
  val isMulsu  = (funct7 === "b0000001".U) && (funct3 === "b010".U) && isRop
  val isMulu   = (funct7 === "b0000001".U) && (funct3 === "b011".U) && isRop
  val isDiv    = (funct7 === "b0000001".U) && (funct3 === "b100".U) && isRop
  val isDivu   = (funct7 === "b0000001".U) && (funct3 === "b101".U) && isRop
  val isRem    = (funct7 === "b0000001".U) && (funct3 === "b110".U) && isRop
  val isRemu   = (funct7 === "b0000001".U) && (funct3 === "b111".U) && isRop
  io.lui      := isLui
  io.jalr     := isJalr
  io.ecall    := isEcall
  io.mret     := isMret
  io.auipc    := isAuipc
  io.I        := isLoad || isJalr || isOpImm
  io.S        := isStore
  io.B        := isBranch
  io.U        := isLui || isAuipc
  io.J        := isJal
  io.R        := isRop
  io.dobranch := isBranch && MuxLookup(funct3, false.B)(
    Seq(
      "b000".U -> (io.rs1data === io.rs2data),             // beq
      "b001".U -> (io.rs1data =/= io.rs2data),             // bne
      "b100".U -> (io.rs1data < io.rs2data),               // blt
      "b101".U -> (io.rs1data >= io.rs2data),              // bge
      "b110".U -> (io.rs1data.asUInt < io.rs2data.asUInt), // bltu
      "b111".U -> (io.rs1data.asUInt >= io.rs2data.asUInt) // bgeu
    )
  )

  io.alu_add   := isAdd
  io.alu_lh20  := isLui || isAuipc
  io.mem_load  := isLoad
  io.alu_sub   := isSub
  io.alu_slt   := isSlt
  io.alu_sltu  := isSltu
  io.alu_xor   := isXor
  io.alu_or    := isOr
  io.alu_and   := isAnd
  io.alu_sll   := isSll
  io.alu_srl   := isSrl
  io.alu_sra   := isSra
  io.alu_mul   := isMul
  io.alu_mulh  := isMulh
  io.alu_mulsu := isMulsu
  io.alu_mulu  := isMulu
  io.alu_div   := isDiv
  io.alu_divu  := isDivu
  io.alu_rem   := isRem
  io.alu_remu  := isRemu
  io.csr       := isCsr
  io.csr_write := isWrite
  io.csr_set   := isSet
  io.csr_clear := isClear

  val Imm = Wire(SInt(32.W))
  Imm        := MuxLookup(Cat(io.I, io.S, io.B, io.U, io.J), 0.S)(
    Seq(
      "b10000".U -> I_imm, // I-type
      "b01000".U -> S_imm, // S-type
      "b00100".U -> B_imm, // B-type
      "b00010".U -> U_imm, // U-type
      "b00001".U -> J_imm  // J-type
    )
  )
  io.rs1addr := inst(19, 15)
  io.rs2addr := inst(24, 20)
  io.rdaddr  := inst(11, 7)
  // alu_a select
  io.alu_a   := MuxCase(
    io.rs1data,
    Seq(
      isAuipc  -> pc.asSInt, // AUIPC: alu_a = PC
      isJal    -> pc.asSInt, // JAL: alu_a = PC
      isBranch -> pc.asSInt
    )
  )

  // alu_b select

  io.alu_b := MuxCase(
    Imm, // if not R-type ,B is Imm
    Seq(
      isRop                                                   -> io.rs2data, // R-type: alu_b = rs2
      (isRop && (funct3 === "b101".U || funct3 === "b001".U)) -> io.rs2data
    )
  )

  io.csraddr := inst(31, 20)
}
