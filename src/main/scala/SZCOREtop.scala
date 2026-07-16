package SZCORE
import chisel3._
import chisel3.util._
class SZCOREtop extends RawModule {
  val io = IO(new Bundle {
    val cpu_clk = Input(Clock())
    val cpu_rst = Input(Bool())
    val ifetch_req = Output(Bool())
    val ifetch_addr = Output(UInt(32.W))
    val ifetch_valid = Input(Bool())
    val ifetch_inst = Input(UInt(32.W))
    val daccess_ren = Output(UInt(4.W))
    val daccess_addr = Output(UInt(32.W))
    val daccess_rvalid = Input(Bool())
    val daccess_rdata = Input(UInt(32.W))
    val daccess_wen = Output(UInt(4.W))
    val daccess_wdata = Output(UInt(32.W))
    val daccess_wresp = Input(Bool())
    val inst    = Output(UInt(32.W))
    val pc      = Output(UInt(32.W))
  })

  withClockAndReset(io.cpu_clk, io.cpu_rst) {
  val ifu           = Module(new IFU)
  val idu           = Module(new IDU)
  val exu           = Module(new EXU)
  val lsu           = Module(new LSU)
  val wbu           = Module(new WBU)
  val gpr           = Module(new GPR)
  val csr           = Module(new CSR)

  val ifu_inst           = ifu.io.inst
  val ifu_pc             = ifu.io.pc
  val idu_jump           = idu.io.dobranch || idu.io.J || idu.io.jalr || idu.io.ecall || idu.io.mret
  val csr_readval        = csr.io.readval
  val exu_result         = exu.io.result
  val lsu_loaddata       = lsu.io.lsu_loaddata
  val gpr_rs1data        = gpr.io.rs1data
  val gpr_rs2data        = gpr.io.rs2data
  val lsu_addr           = lsu.io.lsu_addr
  val lsu_wdata          = lsu.io.lsu_wdata
  val lsu_wmask          = lsu.io.lsu_wmask
  val lsu_wen            = lsu.io.lsu_wen
  val ifu_raddr          = ifu.io.ifu_raddr
  val gpr_we             =
    (!idu.io.S) && (idu.io.R || idu.io.I || idu.io.U || idu.io.J || idu.io.mem_load || idu.io.csr)
  val wbu_writeback_data = wbu.io.writeback_data
  val gpr_a0val          = gpr.io.a0val
  val csr_we             = idu.io.csr_write
  val csr_addr           = idu.io.csraddr
  val csr_op_val         = gpr_rs1data
  val csr_clear          = idu.io.csr_clear
  val csr_set            = idu.io.csr_set
  val csr_write          = idu.io.csr_write
  val csr_pc             = ifu_pc.asUInt
  val csr_ecall          = idu.io.ecall
  val csr_mret           = idu.io.mret
  val jumptarget         = Mux(idu.io.mret, csr_readval, exu_result)

  val fetchOutstanding = RegInit(false.B)
  val memPending       = RegInit(false.B)
  val memIsLoad        = RegInit(false.B)
  val memRd            = RegInit(0.U(5.W))
  val memFunct3        = RegInit(0.U(3.W))
  val memAddr          = RegInit(0.U(32.W))
  val daccessRenReg    = RegInit(0.U(4.W))
  val daccessAddrReg   = RegInit(0.U(32.W))
  val daccessWenReg    = RegInit(0.U(4.W))
  val daccessWdataReg  = RegInit(0.U(32.W))

  val fetchAccept = io.ifetch_valid && fetchOutstanding
  val isMemAccess = idu.io.mem_load || idu.io.S
  val memDone = memPending && Mux(memIsLoad, io.daccess_rvalid, io.daccess_wresp)
  val instructionFinished = (fetchAccept && !isMemAccess) || memDone
  val issueMemoryAccess = fetchAccept && isMemAccess
  val normalWriteback = fetchAccept && !isMemAccess && gpr_we

  io.ifetch_req  := !io.cpu_rst && !fetchOutstanding && !memPending && !io.ifetch_valid
  io.ifetch_addr := ifu_raddr
  io.daccess_ren := daccessRenReg
  io.daccess_addr := daccessAddrReg
  io.daccess_wen := daccessWenReg
  io.daccess_wdata := daccessWdataReg

  when(io.ifetch_req) {
    fetchOutstanding := true.B
  }
  when(fetchAccept) {
    fetchOutstanding := false.B
  }

  daccessRenReg := 0.U
  daccessWenReg := 0.U
  when(issueMemoryAccess) {
    memPending := true.B
    memIsLoad := idu.io.mem_load
    memRd := idu.io.rdaddr
    memFunct3 := ifu_inst(14, 12)
    memAddr := lsu_addr
    daccessAddrReg := lsu_addr
    daccessWdataReg := lsu_wdata.asUInt
    daccessRenReg := Mux(idu.io.mem_load, lsu_wmask(3, 0), 0.U)
    daccessWenReg := Mux(idu.io.S, lsu_wmask(3, 0), 0.U)
  }
  when(memDone) {
    memPending := false.B
  }

  ifu.io.ifu_rdata     := io.ifetch_inst
  ifu.io.fetch         := instructionFinished
  ifu.io.jump          := idu_jump
  ifu.io.jumpTarget    := jumptarget.asUInt

  idu.io.inst      := Mux(fetchAccept, ifu_inst, "h00000013".U)
  idu.io.pc        := ifu_pc.asUInt
  idu.io.rs1data := gpr_rs1data
  idu.io.rs2data := gpr_rs2data

  exu.io.a    := idu.io.alu_a
  exu.io.b    := idu.io.alu_b
  exu.io.add  := idu.io.alu_add
  exu.io.lh20 := idu.io.alu_lh20
  exu.io.sub  := idu.io.alu_sub
  exu.io.slt  := idu.io.alu_slt
  exu.io.sltu := idu.io.alu_sltu
  exu.io.xor  := idu.io.alu_xor
  exu.io.or   := idu.io.alu_or
  exu.io.and  := idu.io.alu_and
  exu.io.sll  := idu.io.alu_sll
  exu.io.srl  := idu.io.alu_srl
  exu.io.sra  := idu.io.alu_sra
  exu.io.mul   := idu.io.alu_mul
  exu.io.mulh  := idu.io.alu_mulh
  exu.io.mulsu := idu.io.alu_mulsu
  exu.io.mulu  := idu.io.alu_mulu
  exu.io.div   := idu.io.alu_div
  exu.io.divu  := idu.io.alu_divu
  exu.io.rem   := idu.io.alu_rem
  exu.io.remu  := idu.io.alu_remu

  lsu.io.funct3        := ifu_inst(14, 12)
  lsu.io.load_funct3   := Mux(memPending, memFunct3, ifu_inst(14, 12))
  lsu.io.load_addr     := Mux(memPending, memAddr, lsu_addr)
  lsu.io.gpr_rs2data   := gpr_rs2data
  lsu.io.exu_result    := exu_result
  lsu.io.lsu_rdata     := io.daccess_rdata
  lsu.io.load          := idu.io.mem_load
  lsu.io.store         := idu.io.S

  wbu.io.exuresult := exu_result
  wbu.io.mem_rdata := lsu_loaddata
  wbu.io.pc        := ifu_pc
  wbu.io.mem_load  := idu.io.mem_load
  wbu.io.J         := idu.io.J
  wbu.io.jalr      := idu.io.jalr
  wbu.io.csr       := idu.io.csr
  wbu.io.csrval    := csr_readval

  gpr.io.rs1addr := idu.io.rs1addr
  gpr.io.rs2addr := idu.io.rs2addr
  gpr.io.we      := normalWriteback || (memDone && memIsLoad)
  gpr.io.rdaddr  := Mux(memDone && memIsLoad, memRd, idu.io.rdaddr)
  gpr.io.rddata  := Mux(memDone && memIsLoad, lsu_loaddata.asSInt, wbu_writeback_data)

  csr.io.we     := csr_we && fetchAccept && !isMemAccess
  csr.io.addr   := csr_addr
  csr.io.op_val := csr_op_val
  csr.io.clear  := csr_clear
  csr.io.set    := csr_set
  csr.io.write  := csr_write && fetchAccept && !isMemAccess
  csr.io.pc     := csr_pc
  csr.io.ecall  := csr_ecall && fetchAccept && !isMemAccess
  csr.io.mret   := csr_mret && fetchAccept && !isMemAccess

  io.inst      := ifu_inst
  io.pc        := ifu_pc.asUInt
  }
}


