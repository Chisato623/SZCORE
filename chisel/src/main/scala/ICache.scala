package SZCORE

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/**
  * Blocking, direct-mapped instruction cache backed by an AXI4 read master.
  * A miss requests one four-beat, 32-bit INCR burst and fills one 16-byte line.
  */
class ICache(
    val sets: Int = 64,
    val wordsPerLine: Int = 4,
    val axiConfig: AXIConfig = AXIConfig()
) extends Module {
  require(sets >= 2 && isPow2(sets), "sets must be a power of two")
  require(wordsPerLine == 4, "this implementation uses a four-word cache line")
  require(axiConfig.addrWidth == 32, "the CPU uses 32-bit addresses")
  require(axiConfig.dataWidth == 32, "the CPU fetches 32-bit instructions")

  private val byteOffsetBits = 2
  private val wordOffsetBits = log2Ceil(wordsPerLine)
  private val indexBits = log2Ceil(sets)
  private val lineOffsetBits = byteOffsetBits + wordOffsetBits
  private val tagBits = axiConfig.addrWidth - lineOffsetBits - indexBits

  val io = IO(new Bundle {
    val cpuReq = Input(Bool())
    val cpuAddr = Input(UInt(axiConfig.addrWidth.W))
    val cpuRespValid = Output(Bool())
    val cpuRespInst = Output(UInt(axiConfig.dataWidth.W))
    val axi = new AXIMaster(axiConfig)
  })

  val validArray = RegInit(VecInit(Seq.fill(sets)(false.B)))
  val tagArray = Reg(Vec(sets, UInt(tagBits.W)))
  val dataArray = Reg(Vec(sets, UInt((wordsPerLine * axiConfig.dataWidth).W)))

  val sIdle :: sLookup :: sReadAddress :: sReadData :: sRespond :: Nil = Enum(5)
  val state = RegInit(sIdle)
  val nextState = WireDefault(state)

  val requestAddr = Reg(UInt(axiConfig.addrWidth.W))
  val responseInst = Reg(UInt(axiConfig.dataWidth.W))
  val fillWordOffset = RegInit(0.U(wordOffsetBits.W))

  val requestIndex = requestAddr(lineOffsetBits + indexBits - 1, lineOffsetBits)
  val requestTag = requestAddr(axiConfig.addrWidth - 1, lineOffsetBits + indexBits)
  val requestWordOffset = requestAddr(lineOffsetBits - 1, byteOffsetBits)
  val lineBase = Cat(requestAddr(axiConfig.addrWidth - 1, lineOffsetBits), 0.U(lineOffsetBits.W))

  val cacheLine = dataArray(requestIndex)
  val hit = validArray(requestIndex) && tagArray(requestIndex) === requestTag
  val hitInst = MuxLookup(requestWordOffset, 0.U(axiConfig.dataWidth.W))(Seq(
    0.U -> cacheLine(31, 0),
    1.U -> cacheLine(63, 32),
    2.U -> cacheLine(95, 64),
    3.U -> cacheLine(127, 96)
  ))

  val arHandshake = io.axi.ar.arvalid && io.axi.ar.arready
  val rHandshake = io.axi.r.rvalid && io.axi.r.rready
  val fillEnable = state === sReadData && rHandshake
  val fillLast = fillEnable && io.axi.r.rlast
  val fillTargetWord = fillEnable && fillWordOffset === requestWordOffset
  val missStart = state === sLookup && !hit

  nextState := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(io.cpuReq, sLookup, sIdle),
    sLookup -> Mux(hit, sRespond, sReadAddress),
    sReadAddress -> Mux(arHandshake, sReadData, sReadAddress),
    sReadData -> Mux(fillLast, sRespond, sReadData),
    sRespond -> sIdle
  ))
  state := nextState

  requestAddr := Mux(state === sIdle && io.cpuReq, io.cpuAddr, requestAddr)
  responseInst := Mux(
    state === sLookup && hit,
    hitInst,
    Mux(fillTargetWord, io.axi.r.rdata, responseInst)
  )
  fillWordOffset := Mux(
    missStart,
    0.U(wordOffsetBits.W),
    Mux(fillEnable && !io.axi.r.rlast, fillWordOffset + 1.U, fillWordOffset)
  )

  val filledLine = MuxLookup(fillWordOffset, cacheLine)(Seq(
    0.U -> Cat(cacheLine(127, 32), io.axi.r.rdata),
    1.U -> Cat(cacheLine(127, 64), io.axi.r.rdata, cacheLine(31, 0)),
    2.U -> Cat(cacheLine(127, 96), io.axi.r.rdata, cacheLine(63, 0)),
    3.U -> Cat(io.axi.r.rdata, cacheLine(95, 0))
  ))
  dataArray(requestIndex) := Mux(fillEnable, filledLine, cacheLine)
  tagArray(requestIndex) := Mux(fillLast, requestTag, tagArray(requestIndex))
  validArray(requestIndex) := Mux(fillLast, true.B, validArray(requestIndex))

  io.cpuRespValid := state === sRespond
  io.cpuRespInst := responseInst

  io.axi.ar.arid := 0.U
  io.axi.ar.araddr := lineBase
  io.axi.ar.arlen := (wordsPerLine - 1).U(8.W)
  io.axi.ar.arsize := byteOffsetBits.U(3.W)
  io.axi.ar.arburst := AXI.BurstIncr
  io.axi.ar.arlock := false.B
  io.axi.ar.arcache := 0.U
  io.axi.ar.arprot := "b100".U
  io.axi.ar.arqos := 0.U
  io.axi.ar.arregion := 0.U
  io.axi.ar.arvalid := state === sReadAddress
  io.axi.r.rready := state === sReadData

  io.axi.aw.awid := 0.U
  io.axi.aw.awaddr := 0.U
  io.axi.aw.awlen := 0.U
  io.axi.aw.awsize := 0.U
  io.axi.aw.awburst := AXI.BurstFixed
  io.axi.aw.awlock := false.B
  io.axi.aw.awcache := 0.U
  io.axi.aw.awprot := 0.U
  io.axi.aw.awqos := 0.U
  io.axi.aw.awregion := 0.U
  io.axi.aw.awvalid := false.B
  io.axi.w.wdata := 0.U
  io.axi.w.wstrb := 0.U
  io.axi.w.wlast := false.B
  io.axi.w.wvalid := false.B
  io.axi.b.bready := false.B
}

object GenerateICache extends App {
  ChiselStage.emitSystemVerilog(
    new ICache,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl", "-disable-all-randomization", "-strip-debug-info")
  )
}
