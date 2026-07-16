package SZCORE
import chisel3.util._
import chisel3._
class WBU extends Module {
  val io      = IO(new Bundle {
    val exuresult      = Input(SInt(32.W))
    val mem_rdata      = Input(UInt(32.W))
    val pc             = Input(SInt(32.W))
    val mem_load       = Input(Bool())
    val J              = Input(Bool())
    val jalr           = Input(Bool())
    val csr            = Input(Bool())
    val csrval         = Input(SInt(32.W))
    val writeback_data = Output(SInt(32.W))
  })
  val wb_data = Wire(SInt(32.W))
  wb_data           := MuxCase(
    io.exuresult,
    Seq(
      io.mem_load       -> io.mem_rdata.asSInt,
      (io.J || io.jalr) -> (io.pc + 4.S),
      io.csr            -> io.csrval
    )
  )
  io.writeback_data := wb_data
}
