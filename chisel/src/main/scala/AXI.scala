package SZCORE

import chisel3._

/** AXI4 signal widths used by the CPU-side master ports. */
case class AXIConfig(
    addrWidth: Int = 32,
    dataWidth: Int = 32,
    idWidth: Int = 4
) {
  require(dataWidth % 8 == 0, "AXI data width must be byte aligned")
}

object AXI {
  val BurstFixed = 0.U(2.W)
  val BurstIncr = 1.U(2.W)
  val RespOkay = 0.U(2.W)
}

class AXIARChannel(val config: AXIConfig) extends Bundle {
  val arid = Output(UInt(config.idWidth.W))
  val araddr = Output(UInt(config.addrWidth.W))
  val arlen = Output(UInt(8.W))
  val arsize = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))
  val arlock = Output(Bool())
  val arcache = Output(UInt(4.W))
  val arprot = Output(UInt(3.W))
  val arqos = Output(UInt(4.W))
  val arregion = Output(UInt(4.W))
  val arvalid = Output(Bool())
  val arready = Input(Bool())
}

class AXIRChannel(val config: AXIConfig) extends Bundle {
  val rid = Input(UInt(config.idWidth.W))
  val rdata = Input(UInt(config.dataWidth.W))
  val rresp = Input(UInt(2.W))
  val rlast = Input(Bool())
  val rvalid = Input(Bool())
  val rready = Output(Bool())
}

class AXIAWChannel(val config: AXIConfig) extends Bundle {
  val awid = Output(UInt(config.idWidth.W))
  val awaddr = Output(UInt(config.addrWidth.W))
  val awlen = Output(UInt(8.W))
  val awsize = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))
  val awlock = Output(Bool())
  val awcache = Output(UInt(4.W))
  val awprot = Output(UInt(3.W))
  val awqos = Output(UInt(4.W))
  val awregion = Output(UInt(4.W))
  val awvalid = Output(Bool())
  val awready = Input(Bool())
}

class AXIWChannel(val config: AXIConfig) extends Bundle {
  val wdata = Output(UInt(config.dataWidth.W))
  val wstrb = Output(UInt((config.dataWidth / 8).W))
  val wlast = Output(Bool())
  val wvalid = Output(Bool())
  val wready = Input(Bool())
}

class AXIBChannel(val config: AXIConfig) extends Bundle {
  val bid = Input(UInt(config.idWidth.W))
  val bresp = Input(UInt(2.W))
  val bvalid = Input(Bool())
  val bready = Output(Bool())
}

/** Master-oriented AXI4 interface. Optional AXI USER signals are omitted. */
class AXIMaster(val config: AXIConfig = AXIConfig()) extends Bundle {
  val ar = new AXIARChannel(config)
  val r = new AXIRChannel(config)
  val aw = new AXIAWChannel(config)
  val w = new AXIWChannel(config)
  val b = new AXIBChannel(config)
}

class AXISlave(config: AXIConfig = AXIConfig()) extends Bundle {
  val ar = Flipped(new AXIARChannel(config))
  val r = Flipped(new AXIRChannel(config))
  val aw = Flipped(new AXIAWChannel(config))
  val w = Flipped(new AXIWChannel(config))
  val b = Flipped(new AXIBChannel(config))
}
