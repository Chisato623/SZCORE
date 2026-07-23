package SZCORE
import chisel3._

class AXILiteARChannel extends Bundle {
  val araddr  = Output(UInt(32.W))
  val arvalid = Output(Bool())
  val arready = Input(Bool())
}

class AXILiteRChannel extends Bundle {
  val rdata  = Input(SInt(32.W))
  val rresp  = Input(UInt(2.W))
  val rvalid = Input(Bool())
  val rready = Output(Bool())
}

class AXILiteAWChannel extends Bundle {
  val awaddr  = Output(UInt(32.W))
  val awvalid = Output(Bool())
  val awready = Input(Bool())
}

class AXILiteWChannel extends Bundle {
  val wdata  = Output(SInt(32.W))
  val wstrb  = Output(UInt(4.W))
  val wvalid = Output(Bool())
  val wready = Input(Bool())
}

class AXILiteBChannel extends Bundle {
  val bresp  = Input(UInt(2.W))
  val bvalid = Input(Bool())
  val bready = Output(Bool())
}

class AXILiteMaster extends Bundle {
  val ar = new AXILiteARChannel
  val r  = new AXILiteRChannel
  val aw = new AXILiteAWChannel
  val w  = new AXILiteWChannel
  val b  = new AXILiteBChannel
}

class AXILiteSlave extends Bundle {
  val ar = Flipped(new AXILiteARChannel)
  val r  = Flipped(new AXILiteRChannel)
  val aw = Flipped(new AXILiteAWChannel)
  val w  = Flipped(new AXILiteWChannel)
  val b  = Flipped(new AXILiteBChannel)
}
