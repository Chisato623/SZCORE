package SZCORE

import chisel3._
import chisel3.util._

class LSU extends Module {
  val io = IO(new Bundle {
    val exu_result = Input(SInt(32.W))
    val funct3 = Input(UInt(3.W))
    val load_funct3 = Input(UInt(3.W))
    val load_addr = Input(UInt(32.W))
    val gpr_rs2data = Input(SInt(32.W))
    val load = Input(Bool())
    val store = Input(Bool())
    val lsu_addr = Output(UInt(32.W))
    val lsu_wen = Output(Bool())
    val lsu_wdata = Output(SInt(32.W))
    val lsu_wmask = Output(UInt(8.W))
    val lsu_loaddata = Output(UInt(32.W))
    val lsu_rdata = Input(UInt(32.W))
  })

  val address = io.exu_result.asUInt
  val byteOffset = address(1, 0)
  val loadByteOffset = io.load_addr(1, 0)

  val selectedByte = MuxLookup(loadByteOffset, 0.U(8.W))(Seq(
    0.U -> io.lsu_rdata(7, 0),
    1.U -> io.lsu_rdata(15, 8),
    2.U -> io.lsu_rdata(23, 16),
    3.U -> io.lsu_rdata(31, 24)
  ))
  val selectedHalfword = Mux(
    io.load_addr(1),
    io.lsu_rdata(31, 16),
    io.lsu_rdata(15, 0)
  )

  io.lsu_loaddata := MuxLookup(io.load_funct3, 0.U(32.W))(Seq(
    "b000".U -> Cat(Fill(24, selectedByte(7)), selectedByte),
    "b001".U -> Cat(Fill(16, selectedHalfword(15)), selectedHalfword),
    "b010".U -> io.lsu_rdata,
    "b100".U -> Cat(0.U(24.W), selectedByte),
    "b101".U -> Cat(0.U(16.W), selectedHalfword)
  ))

  val writeMask = WireDefault(0.U(4.W))

  switch(io.funct3) {
    is("b000".U) {
      writeMask := 1.U(4.W) << byteOffset
    }
    is("b001".U) {
      when(!byteOffset(0)) {
        writeMask := Mux(byteOffset(1), "b1100".U, "b0011".U)
      }
    }
    is("b010".U) {
      when(byteOffset === 0.U) {
        writeMask := "b1111".U
      }
    }
  }

  io.lsu_addr := address
  io.lsu_wen := io.store && writeMask.orR
  io.lsu_wdata := io.gpr_rs2data
  io.lsu_wmask := Cat(0.U(4.W), writeMask)
}
