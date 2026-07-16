package SZCORE

import chisel3._
import circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFile
import scala.xml.dtd.IMPLIED
import chisel3.experimental._
import chisel3.util._
class LSU extends Module {
  val io = IO(new Bundle {
    val exu_result    = Input(SInt(32.W))
    val funct3        = Input(UInt(3.W))
    val gpr_rs2data   = Input(SInt(32.W))
    val load          = Input(Bool())
    val store         = Input(Bool())
    val lsu_addr      = Output(UInt(32.W))
    val lsu_wen       = Output(Bool())
    val lsu_wdata     = Output(SInt(32.W))
    val lsu_wmask     = Output(UInt(8.W))
    val lsu_loaddata  = Output(UInt(32.W))
    val lsu_rdata     = Input(UInt(32.W))
  })

  val result = Wire(SInt(32.W))
  val rs2data = io.gpr_rs2data
  val byte_offset = result(1, 0)
  val halfword_offset = result(1)
  val raw_data = io.lsu_rdata

  result := io.exu_result

  val selected_byte = MuxLookup(byte_offset, 0.U)(Seq(
    0.U -> (raw_data(7, 0)),
    1.U -> (raw_data(15, 8)),
    2.U -> (raw_data(23, 16)),
    3.U -> (raw_data(31, 24))
  ))

  val selected_halfword = MuxLookup(halfword_offset, 0.U)(Seq(
    0.U -> (raw_data(15, 0)),
    1.U -> (raw_data(31, 16))
  ))

  val load_data = WireInit(0.U(32.W))
  load_data := MuxLookup(io.funct3, 0.U)(Seq(
    0b100.U -> Cat(Fill(24, 0.U), selected_byte),
    0b010.U -> raw_data,
    0b001.U -> Cat(Fill(16, selected_halfword(15)), selected_halfword),
    0b000.U -> Cat(Fill(24, selected_byte(7)), selected_byte),
    0b101.U -> Cat(Fill(16, 0.U), selected_halfword)
  ))

  val base_data = MuxLookup(io.funct3, 0.U)(Seq(
    0b000.U -> Cat(0.U(24.W), rs2data(7, 0)).asUInt,
    0b001.U -> Cat(0.U(16.W), rs2data(15, 0)).asUInt,
    0b010.U -> rs2data.asUInt
  ))

  val shiftbyte_data = MuxLookup(byte_offset, 0.U)(Seq(
    0.U -> base_data,
    1.U -> (base_data << 8),
    2.U -> (base_data << 16),
    3.U -> (base_data << 24)
  )).asUInt

  val shifthalfword_data = MuxLookup(halfword_offset, 0.U)(Seq(
    0.U -> base_data,
    1.U -> (base_data << 16)
  ))

  val shifted_data = MuxLookup(io.funct3, 0.U)(Seq(
    0b000.U -> shiftbyte_data,
    0b001.U -> shifthalfword_data,
    0b010.U -> base_data
  ))

  val base_mask = MuxLookup(io.funct3, 0.U)(Seq(
    0b000.U -> 0x1.U,
    0b001.U -> 0x3.U,
    0b010.U -> 0xF.U
  ))

  val wmask = (base_mask << byte_offset).asUInt



  io.lsu_addr      := result.asUInt
  io.lsu_wdata     := shifted_data.asSInt
  io.lsu_wmask     := wmask
  io.lsu_wen       := io.store
  io.lsu_loaddata  :=  load_data
}
