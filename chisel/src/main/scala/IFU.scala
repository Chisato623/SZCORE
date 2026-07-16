package SZCORE
import chisel3.util._
import chisel3._
class IFU extends Module {
  val io = IO(new Bundle {
    val fetch = Input(Bool())
    val jump =Input(Bool())
    val jumpTarget = Input(UInt(32.W))
    val ifu_raddr = Output(UInt(32.W))
    val ifu_rdata = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
    val pc = Output(SInt(32.W))
  })
val pcReg =RegInit(0.S(32.W))
val npc =Mux(io.jump, io.jumpTarget.asSInt, pcReg + 4.S)
when(io.fetch) {
  pcReg := npc
}

io.inst := io.ifu_rdata
io.pc := pcReg
io.ifu_raddr := pcReg.asUInt
}
