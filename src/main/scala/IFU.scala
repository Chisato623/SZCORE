package SZCORE
import chisel3.util._
import chisel3._
class IFU extends Module {
  val io = IO(new Bundle {
    val jump =Input(Bool())
    val jumpTarget = Input(UInt(32.W))
    val ifu_raddr = Output(UInt(32.W))
    val ifu_rdata = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
    val pc = Output(SInt(32.W))
  })
val pcReg =RegInit(0.S(32.W))//should know that the initial value of the program counter (pcReg) is set to 0. This means that when the IFU module is instantiated, the program counter will start at address 0.
val npc =Mux(io.jump, io.jumpTarget.asSInt, pcReg + 4.S)
pcReg := npc

io.inst := io.ifu_rdata
io.pc := pcReg
io.ifu_raddr := npc.asUInt
}
