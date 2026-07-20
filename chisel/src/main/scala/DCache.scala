package SZCORE

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/**
  * Blocking, direct-mapped data cache with an AXI4 master interface.
  *
  * Loads allocate a four-word line through one AXI INCR burst. Stores are
  * write-through and no-write-allocate: a store hit updates the cached bytes
  * and every store is issued as a single-beat AXI write.
  */
class DCache(
    val sets: Int = 64,
    val wordsPerLine: Int = 4,
    val axiConfig: AXIConfig = AXIConfig()
) extends Module {
  require(sets >= 2 && isPow2(sets), "sets must be a power of two")
  require(wordsPerLine == 4, "this implementation uses a four-word cache line")
  require(axiConfig.addrWidth == 32, "the CPU uses 32-bit addresses")
  require(axiConfig.dataWidth == 32, "the CPU data path is 32 bits")

  private val byteOffsetBits = 2
  private val wordOffsetBits = log2Ceil(wordsPerLine)
  private val indexBits = log2Ceil(sets)
  private val lineOffsetBits = byteOffsetBits + wordOffsetBits
  private val tagBits = axiConfig.addrWidth - lineOffsetBits - indexBits

  val io = IO(new Bundle {
    val cpuReq = Input(Bool())
    val cpuWrite = Input(Bool())
    val cpuAddr = Input(UInt(axiConfig.addrWidth.W))
    // Raw store data from the CPU. cpuWStrb selects the addressed byte lanes.
    val cpuWData = Input(UInt(axiConfig.dataWidth.W))
    val cpuWStrb = Input(UInt((axiConfig.dataWidth / 8).W))
    val cpuRespValid = Output(Bool())
    val cpuRespData = Output(UInt(axiConfig.dataWidth.W))
    val axi = new AXIMaster(axiConfig)
  })

  val validArray = RegInit(VecInit(Seq.fill(sets)(false.B)))
  val tagArray = Reg(Vec(sets, UInt(tagBits.W)))
  val dataArray = Reg(Vec(sets, UInt((wordsPerLine * axiConfig.dataWidth).W)))

  val (sIdle :: sLookup :: sReadAddress :: sReadData :: sWriteAddress ::
    sWriteData :: sWriteResponse :: sRespond :: Nil) = Enum(8)
  val state = RegInit(sIdle)
  val nextState = WireDefault(state)

  val requestAddr = Reg(UInt(axiConfig.addrWidth.W))
  val requestWrite = RegInit(false.B)
  val requestWData = Reg(UInt(axiConfig.dataWidth.W))
  val requestWStrb = Reg(UInt((axiConfig.dataWidth / 8).W))
  val responseData = Reg(UInt(axiConfig.dataWidth.W))
  val fillWordOffset = RegInit(0.U(wordOffsetBits.W))

  val requestIndex = requestAddr(lineOffsetBits + indexBits - 1, lineOffsetBits)
  val requestTag = requestAddr(axiConfig.addrWidth - 1, lineOffsetBits + indexBits)
  val requestWordOffset = requestAddr(lineOffsetBits - 1, byteOffsetBits)
  val lineBase = Cat(requestAddr(axiConfig.addrWidth - 1, lineOffsetBits), 0.U(lineOffsetBits.W))

  val cacheLine = dataArray(requestIndex)
  val cacheWord = MuxLookup(requestWordOffset, 0.U(axiConfig.dataWidth.W))(Seq(
    0.U -> cacheLine(31, 0),
    1.U -> cacheLine(63, 32),
    2.U -> cacheLine(95, 64),
    3.U -> cacheLine(127, 96)
  ))
  val hit = validArray(requestIndex) && tagArray(requestIndex) === requestTag

  // The core provides raw store data; AXI WDATA must be aligned to WSTRB lanes.
  val alignedStoreData = MuxLookup(requestWStrb, requestWData)(Seq(
    "b0010".U -> Cat(0.U(16.W), requestWData(7, 0), 0.U(8.W)),
    "b0100".U -> Cat(0.U(8.W), requestWData(7, 0), 0.U(16.W)),
    "b1000".U -> Cat(requestWData(7, 0), 0.U(24.W)),
    "b1100".U -> Cat(requestWData(15, 0), 0.U(16.W))
  ))
  val byteMask = Cat(
    Fill(8, requestWStrb(3)),
    Fill(8, requestWStrb(2)),
    Fill(8, requestWStrb(1)),
    Fill(8, requestWStrb(0))
  )
  val mergedStoreWord = (cacheWord & ~byteMask) | (alignedStoreData & byteMask)
  val storeHit = state === sLookup && requestWrite && hit
  val storeLine = MuxLookup(requestWordOffset, cacheLine)(Seq(
    0.U -> Cat(cacheLine(127, 32), mergedStoreWord),
    1.U -> Cat(cacheLine(127, 64), mergedStoreWord, cacheLine(31, 0)),
    2.U -> Cat(cacheLine(127, 96), mergedStoreWord, cacheLine(63, 0)),
    3.U -> Cat(mergedStoreWord, cacheLine(95, 0))
  ))

  val arHandshake = io.axi.ar.arvalid && io.axi.ar.arready
  val rHandshake = io.axi.r.rvalid && io.axi.r.rready
  val awHandshake = io.axi.aw.awvalid && io.axi.aw.awready
  val wHandshake = io.axi.w.wvalid && io.axi.w.wready
  val bHandshake = io.axi.b.bvalid && io.axi.b.bready
  val fillEnable = state === sReadData && rHandshake
  val fillLast = fillEnable && io.axi.r.rlast
  val fillTargetWord = fillEnable && fillWordOffset === requestWordOffset
  val missStart = state === sLookup && !requestWrite && !hit

  nextState := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(io.cpuReq, sLookup, sIdle),
    sLookup -> Mux(requestWrite, sWriteAddress, Mux(hit, sRespond, sReadAddress)),
    sReadAddress -> Mux(arHandshake, sReadData, sReadAddress),
    sReadData -> Mux(fillLast, sRespond, sReadData),
    sWriteAddress -> Mux(awHandshake, sWriteData, sWriteAddress),
    sWriteData -> Mux(wHandshake, sWriteResponse, sWriteData),
    sWriteResponse -> Mux(bHandshake, sRespond, sWriteResponse),
    sRespond -> sIdle
  ))
  state := nextState

  requestAddr := Mux(state === sIdle && io.cpuReq, io.cpuAddr, requestAddr)
  requestWrite := Mux(state === sIdle && io.cpuReq, io.cpuWrite, requestWrite)
  requestWData := Mux(state === sIdle && io.cpuReq, io.cpuWData, requestWData)
  requestWStrb := Mux(state === sIdle && io.cpuReq, io.cpuWStrb, requestWStrb)
  responseData := Mux(
    state === sLookup && !requestWrite && hit,
    cacheWord,
    Mux(fillTargetWord, io.axi.r.rdata, responseData)
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
  dataArray(requestIndex) := Mux(
    fillEnable,
    filledLine,
    Mux(storeHit, storeLine, cacheLine)
  )
  tagArray(requestIndex) := Mux(fillLast, requestTag, tagArray(requestIndex))
  validArray(requestIndex) := Mux(fillLast, true.B, validArray(requestIndex))

  io.cpuRespValid := state === sRespond
  io.cpuRespData := responseData

  io.axi.ar.arid := 1.U
  io.axi.ar.araddr := lineBase
  io.axi.ar.arlen := (wordsPerLine - 1).U(8.W)
  io.axi.ar.arsize := byteOffsetBits.U(3.W)
  io.axi.ar.arburst := AXI.BurstIncr
  io.axi.ar.arlock := false.B
  io.axi.ar.arcache := 0.U
  io.axi.ar.arprot := 0.U
  io.axi.ar.arqos := 0.U
  io.axi.ar.arregion := 0.U
  io.axi.ar.arvalid := state === sReadAddress
  io.axi.r.rready := state === sReadData

  io.axi.aw.awid := 1.U
  io.axi.aw.awaddr := requestAddr
  io.axi.aw.awlen := 0.U
  io.axi.aw.awsize := byteOffsetBits.U(3.W)
  io.axi.aw.awburst := AXI.BurstIncr
  io.axi.aw.awlock := false.B
  io.axi.aw.awcache := 0.U
  io.axi.aw.awprot := 0.U
  io.axi.aw.awqos := 0.U
  io.axi.aw.awregion := 0.U
  io.axi.aw.awvalid := state === sWriteAddress
  io.axi.w.wdata := alignedStoreData
  io.axi.w.wstrb := requestWStrb
  io.axi.w.wlast := true.B
  io.axi.w.wvalid := state === sWriteData
  io.axi.b.bready := state === sWriteResponse
}

object GenerateDCache extends App {
  ChiselStage.emitSystemVerilog(
    new DCache,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl/dcache", "-disable-all-randomization", "-strip-debug-info")
  )
}
