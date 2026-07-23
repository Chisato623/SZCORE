package SZCORE

import chisel3._
import chisel3.util._

class IFUMessage extends Bundle {
  val inst = Output(UInt(32.W))
  val pc = Output(SInt(32.W))
}

/** Program counter and ICache request/response adapter. */
class IFU extends Module {
  val io = IO(new Bundle {
    val out = Decoupled(new IFUMessage)
    val cacheReq = Output(Bool())
    val cacheAddr = Output(UInt(32.W))
    val cacheRespValid = Input(Bool())
    val cacheRespInst = Input(UInt(32.W))
    val jump = Input(Bool())
    val jumptarget = Input(SInt(32.W))
    val stall = Input(Bool())
  })

  val sIdle :: sWaitCache :: sOutput :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val pcReg = RegInit(0.S(32.W))
  val requestPc = RegInit(0.S(32.W))
  val outInst = RegInit(0.U(32.W))
  val outPc = RegInit(0.S(32.W))

  val issue = state === sIdle && !io.stall
  val receive = state === sWaitCache && io.cacheRespValid
  val redirect = state === sOutput && io.out.valid && io.jump

  state := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(issue, sWaitCache, sIdle),
    sWaitCache -> Mux(receive, sOutput, sWaitCache),
    sOutput -> Mux(redirect || io.out.ready, sIdle, sOutput)
  ))
  pcReg := Mux(redirect, io.jumptarget, Mux(issue, pcReg + 4.S, pcReg))
  requestPc := Mux(issue, pcReg, requestPc)
  outInst := Mux(receive, io.cacheRespInst, outInst)
  outPc := Mux(receive, requestPc, outPc)

  io.cacheReq := issue
  io.cacheAddr := pcReg.asUInt
  io.out.valid := state === sOutput
  io.out.bits.inst := outInst
  io.out.bits.pc := outPc
}
