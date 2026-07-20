package SZCORE

import chisel3._
import chisel3.util._

/** Load/store adapter for DCache's blocking request/response interface. */
class LSU extends Module {
  val io = IO(new Bundle {
    val exuResult = Input(SInt(32.W))
    val funct3 = Input(UInt(3.W))
    val gprRs2Data = Input(SInt(32.W))
    val load = Input(Bool())
    val store = Input(Bool())
    val cacheReq = Output(Bool())
    val cacheWrite = Output(Bool())
    val cacheAddr = Output(UInt(32.W))
    val cacheWData = Output(UInt(32.W))
    val cacheWStrb = Output(UInt(4.W))
    val cacheRespValid = Input(Bool())
    val cacheRespData = Input(UInt(32.W))
    val loadData = Output(SInt(32.W))
    val loading = Output(Bool())
    val busy = Output(Bool())
    val loadDone = Output(Bool())
    val storeDone = Output(Bool())
  })

  val sIdle :: sRequest :: sWait :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val start = state === sIdle && (io.load || io.store)
  val response = state === sWait && io.cacheRespValid

  val addrReg = RegInit(0.U(32.W))
  val funct3Reg = RegInit(0.U(3.W))
  val wdataReg = RegInit(0.U(32.W))
  val writeReg = RegInit(false.B)
  val loadDataReg = RegInit(0.S(32.W))

  state := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(start, sRequest, sIdle),
    sRequest -> sWait,
    sWait -> Mux(response, sIdle, sWait)
  ))
  addrReg := Mux(start, io.exuResult.asUInt, addrReg)
  funct3Reg := Mux(start, io.funct3, funct3Reg)
  wdataReg := Mux(start, io.gprRs2Data.asUInt, wdataReg)
  writeReg := Mux(start, io.store, writeReg)

  val byteOffset = addrReg(1, 0)
  val selectedByte = MuxLookup(byteOffset, 0.U(8.W))(Seq(
    0.U -> io.cacheRespData(7, 0),
    1.U -> io.cacheRespData(15, 8),
    2.U -> io.cacheRespData(23, 16),
    3.U -> io.cacheRespData(31, 24)
  ))
  val selectedHalfword = Mux(
    addrReg(1),
    io.cacheRespData(31, 16),
    io.cacheRespData(15, 0)
  )
  val loadValue = MuxLookup(funct3Reg, 0.U(32.W))(Seq(
    "b000".U -> Cat(Fill(24, selectedByte(7)), selectedByte),
    "b001".U -> Cat(Fill(16, selectedHalfword(15)), selectedHalfword),
    "b010".U -> io.cacheRespData,
    "b100".U -> Cat(0.U(24.W), selectedByte),
    "b101".U -> Cat(0.U(16.W), selectedHalfword)
  ))
  val baseMask = MuxLookup(funct3Reg, 0.U(4.W))(Seq(
    "b000".U -> "b0001".U,
    "b001".U -> "b0011".U,
    "b010".U -> "b1111".U
  ))

  loadDataReg := Mux(response && !writeReg, loadValue.asSInt, loadDataReg)

  io.cacheReq := state === sRequest
  io.cacheWrite := writeReg
  io.cacheAddr := addrReg
  io.cacheWData := wdataReg
  io.cacheWStrb := (baseMask << byteOffset)(3, 0)
  io.loadData := Mux(response && !writeReg, loadValue.asSInt, loadDataReg)
  io.loading := (state === sRequest || state === sWait) && !writeReg
  io.busy := state =/= sIdle
  io.loadDone := response && !writeReg
  io.storeDone := response && writeReg
}
