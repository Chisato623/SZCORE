package SZCORE

import chisel3._
import chisel3.util._

/**
  * One-outstanding-read AXI4 arbiter.
  *
  * ICache has read priority. The read owner is retained until the downstream
  * slave completes the burst with an RLAST handshake. LSU write channels pass
  * through independently because ICache never writes.
  */
class AXI_Master(val config: AXIConfig = AXIConfig()) extends Module {
  val io = IO(new Bundle {
    val icache_axi = Flipped(new AXIMaster(config))
    val lsu_axi = Flipped(new AXIMaster(config))
    val pmem_axi = new AXIMaster(config)
  })

  val sIdle :: sSendAr :: sWaitR :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val owner = RegInit(0.U(1.W)) // 0: ICache, 1: LSU

  val selectICache = state === sIdle && io.icache_axi.ar.arvalid
  val selectLsu = state === sIdle && !io.icache_axi.ar.arvalid && io.lsu_axi.ar.arvalid
  val selectedArValid = Mux(owner === 0.U, io.icache_axi.ar.arvalid, io.lsu_axi.ar.arvalid)

  val arHandshake = io.pmem_axi.ar.arvalid && io.pmem_axi.ar.arready
  val rHandshake = io.pmem_axi.r.rvalid && io.pmem_axi.r.rready
  state := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(selectICache || selectLsu, sSendAr, sIdle),
    sSendAr -> Mux(arHandshake, sWaitR, sSendAr),
    sWaitR -> Mux(rHandshake && io.pmem_axi.r.rlast, sIdle, sWaitR)
  ))
  owner := Mux(selectICache, 0.U, Mux(selectLsu, 1.U, owner))

  io.pmem_axi.ar.arid := Mux(owner === 0.U, io.icache_axi.ar.arid, io.lsu_axi.ar.arid)
  io.pmem_axi.ar.araddr := Mux(owner === 0.U, io.icache_axi.ar.araddr, io.lsu_axi.ar.araddr)
  io.pmem_axi.ar.arlen := Mux(owner === 0.U, io.icache_axi.ar.arlen, io.lsu_axi.ar.arlen)
  io.pmem_axi.ar.arsize := Mux(owner === 0.U, io.icache_axi.ar.arsize, io.lsu_axi.ar.arsize)
  io.pmem_axi.ar.arburst := Mux(owner === 0.U, io.icache_axi.ar.arburst, io.lsu_axi.ar.arburst)
  io.pmem_axi.ar.arlock := Mux(owner === 0.U, io.icache_axi.ar.arlock, io.lsu_axi.ar.arlock)
  io.pmem_axi.ar.arcache := Mux(owner === 0.U, io.icache_axi.ar.arcache, io.lsu_axi.ar.arcache)
  io.pmem_axi.ar.arprot := Mux(owner === 0.U, io.icache_axi.ar.arprot, io.lsu_axi.ar.arprot)
  io.pmem_axi.ar.arqos := Mux(owner === 0.U, io.icache_axi.ar.arqos, io.lsu_axi.ar.arqos)
  io.pmem_axi.ar.arregion := Mux(owner === 0.U, io.icache_axi.ar.arregion, io.lsu_axi.ar.arregion)
  io.pmem_axi.ar.arvalid := state === sSendAr && selectedArValid

  io.icache_axi.ar.arready := state === sSendAr && owner === 0.U && io.pmem_axi.ar.arready
  io.lsu_axi.ar.arready := state === sSendAr && owner === 1.U && io.pmem_axi.ar.arready

  io.icache_axi.r.rid := io.pmem_axi.r.rid
  io.icache_axi.r.rdata := io.pmem_axi.r.rdata
  io.icache_axi.r.rresp := io.pmem_axi.r.rresp
  io.icache_axi.r.rlast := io.pmem_axi.r.rlast
  io.icache_axi.r.rvalid := state === sWaitR && owner === 0.U && io.pmem_axi.r.rvalid

  io.lsu_axi.r.rid := io.pmem_axi.r.rid
  io.lsu_axi.r.rdata := io.pmem_axi.r.rdata
  io.lsu_axi.r.rresp := io.pmem_axi.r.rresp
  io.lsu_axi.r.rlast := io.pmem_axi.r.rlast
  io.lsu_axi.r.rvalid := state === sWaitR && owner === 1.U && io.pmem_axi.r.rvalid
  io.pmem_axi.r.rready := state === sWaitR && Mux(
    owner === 0.U,
    io.icache_axi.r.rready,
    io.lsu_axi.r.rready
  )

  io.pmem_axi.aw.awid := io.lsu_axi.aw.awid
  io.pmem_axi.aw.awaddr := io.lsu_axi.aw.awaddr
  io.pmem_axi.aw.awlen := io.lsu_axi.aw.awlen
  io.pmem_axi.aw.awsize := io.lsu_axi.aw.awsize
  io.pmem_axi.aw.awburst := io.lsu_axi.aw.awburst
  io.pmem_axi.aw.awlock := io.lsu_axi.aw.awlock
  io.pmem_axi.aw.awcache := io.lsu_axi.aw.awcache
  io.pmem_axi.aw.awprot := io.lsu_axi.aw.awprot
  io.pmem_axi.aw.awqos := io.lsu_axi.aw.awqos
  io.pmem_axi.aw.awregion := io.lsu_axi.aw.awregion
  io.pmem_axi.aw.awvalid := io.lsu_axi.aw.awvalid
  io.lsu_axi.aw.awready := io.pmem_axi.aw.awready

  io.pmem_axi.w.wdata := io.lsu_axi.w.wdata
  io.pmem_axi.w.wstrb := io.lsu_axi.w.wstrb
  io.pmem_axi.w.wlast := io.lsu_axi.w.wlast
  io.pmem_axi.w.wvalid := io.lsu_axi.w.wvalid
  io.lsu_axi.w.wready := io.pmem_axi.w.wready

  io.lsu_axi.b.bid := io.pmem_axi.b.bid
  io.lsu_axi.b.bresp := io.pmem_axi.b.bresp
  io.lsu_axi.b.bvalid := io.pmem_axi.b.bvalid
  io.pmem_axi.b.bready := io.lsu_axi.b.bready

  io.icache_axi.aw.awready := false.B
  io.icache_axi.w.wready := false.B
  io.icache_axi.b.bid := 0.U
  io.icache_axi.b.bresp := AXI.RespOkay
  io.icache_axi.b.bvalid := false.B
}
