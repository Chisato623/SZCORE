package SZCORE

import chisel3._
import chisel3.util._
class CSR extends Module {
  val io = IO(new Bundle {
    val addr    = Input(UInt(12.W))
    val op_val  = Input(SInt(32.W))
    val write   = Input(Bool())
    val set     = Input(Bool())
    val ecall   = Input(Bool())
    val mret    = Input(Bool())
    val clear   = Input(Bool())
    val pc      = Input(UInt(32.W))
    val we      = Input(Bool())
    val readval = Output(SInt(32.W))
  })

  val mcycle    = RegInit(0.S(32.W))
  val mcycleh   = RegInit(0.S(32.W))
  val mvendorid = RegInit(0x79737978.S(32.W))
  val marchid   = RegInit(26010017.S(32.W))

  val mstatus = RegInit(0.S(32.W))
  val mtvec   = RegInit(0.S(32.W))
  val mcause  = RegInit(0.S(32.W))
  val mepc    = RegInit(0.S(32.W))

  mcycle  := mcycle + 1.S
  mcycleh := Mux((mcycle === 0xffffffff.S), mcycleh + 1.S, mcycleh)

  def calculate(currentval: SInt, opval: SInt): SInt = {
    MuxCase(
      currentval,
      Seq(
        io.write -> opval,
        io.set   -> (currentval | opval),
        io.clear -> (currentval & ~opval)
      )
    )
  }

  mstatus := Mux(io.ecall, 0x1800.S, Mux((io.addr =/= 0x300.U) || !io.we, mstatus, calculate(mstatus, io.op_val)))

  mtvec := Mux((io.addr =/= 0x305.U) || !io.we, mtvec, calculate(mtvec, io.op_val))

  mepc := Mux(io.ecall, io.pc.asSInt, Mux((io.addr =/= 0x341.U) || !io.we, mepc, calculate(mepc, io.op_val)))

  mcause := Mux(io.ecall, 11.S, Mux((io.addr =/= 0x342.U) || !io.we, mcause, calculate(mcause, io.op_val)))

  io.readval := Mux(
    io.ecall,
    mtvec,
    Mux(
      io.mret,
      mepc,
      MuxLookup(io.addr, 0.S)(
        Seq(
          0xb00.U -> mcycle,
          0xb01.U -> mcycleh,
          0xf11.U -> mvendorid,
          0xf12.U -> marchid,
          0x300.U -> mstatus,
          0x305.U -> mtvec,
          0x341.U -> mepc,
          0x342.U -> mcause
        )
      )
    )
  )
}
