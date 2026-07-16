package SZCORE
import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import scala.xml.dtd.IMPLIED
import chisel3.experimental._
import javax.swing.text.StyledEditorKit.BoldAction

class IROM extends BlackBox {
  override def desiredName = "IROM"

  val io = IO(new Bundle {
    val clka  = Input(Clock())
    val addra = Input(UInt(14.W))
    val douta = Output(UInt(32.W))
  })
}

class DRAM extends BlackBox {
  override def desiredName = "DRAM"

  val io = IO(new Bundle {
    val clka  = Input(Clock())
    val wea   = Input(UInt(4.W))
    val addra = Input(UInt(15.W))
    val dina  = Input(UInt(32.W))
    val douta = Output(UInt(32.W))
  })
}

class SZCOREtop extends RawModule {
  val io = IO(new Bundle {
    val cpu_clk = Input(Clock())
    val cpu_rst = Input(Bool())
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
  val ifu_rdata          = ifu.io.ifu_rdata
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

  val irom = Module(new IROM)
  irom.io.clka := io.cpu_clk
  irom.io.addra := ifu_raddr(15, 2)

  val dram = Module(new DRAM)
  dram.io.clka := io.cpu_clk
  dram.io.wea := Mux(lsu_wen, lsu_wmask(3, 0), 0.U)
  dram.io.addra := lsu_addr(16, 2)
  dram.io.dina := lsu_wdata.asUInt

  ifu.io.ifu_rdata     := irom.io.douta
  ifu.io.jump          := idu_jump
  ifu.io.jumpTarget    := jumptarget.asUInt

  idu.io.inst      := ifu_inst
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
  lsu.io.gpr_rs2data   := gpr_rs2data
  lsu.io.exu_result    := exu_result
  lsu.io.lsu_rdata     := dram.io.douta
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
  gpr.io.we      := gpr_we
  gpr.io.rdaddr  := idu.io.rdaddr
  gpr.io.rddata  := wbu_writeback_data

  csr.io.we     := csr_we
  csr.io.addr   := csr_addr
  csr.io.op_val := csr_op_val
  csr.io.clear  := csr_clear
  csr.io.set    := csr_set
  csr.io.write  := csr_write
  csr.io.pc     := csr_pc
  csr.io.ecall  := csr_ecall
  csr.io.mret   := csr_mret

  io.inst      := ifu_inst
  io.pc        := ifu_pc.asUInt
  }
}


