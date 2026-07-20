package SZCORE

import chisel3._
import chisel3.util._

/** Single-issue core with split ICache/DCache and one shared AXI4 master port. */
class SZCOREtop extends RawModule {
  val io = IO(new Bundle {
    val cpu_clk = Input(Clock())
    val cpu_rst = Input(Bool())
    val pmem_axi = new AXIMaster
    val inst = Output(UInt(32.W))
    val pc = Output(UInt(32.W))
  })

  withClockAndReset(io.cpu_clk, io.cpu_rst) {
    val ifu = Module(new IFU)
    val idu = Module(new IDU)
    val exu = Module(new EXU)
    val lsu = Module(new LSU)
    val wbu = Module(new WBU)
    val gpr = Module(new GPR)
    val csr = Module(new CSR)
    val icache = Module(new ICache)
    val dcache = Module(new DCache)
    val axiMaster = Module(new AXI_Master)

    val ifuInst = ifu.io.out.bits.inst
    val ifuPc = ifu.io.out.bits.pc
    val ifuFire = ifu.io.out.valid && ifu.io.out.ready
    val gprRs1Data = gpr.io.rs1data
    val gprRs2Data = gpr.io.rs2data
    val exuResult = exu.io.result
    val csrReadValue = csr.io.readval
    val jump = idu.io.dobranch || idu.io.J || idu.io.jalr || idu.io.ecall || idu.io.mret
    val jumpTarget = Mux(idu.io.mret, csrReadValue, exuResult)
    val memOperation = idu.io.mem_load || idu.io.S
    val issueMemory = ifuFire && memOperation
    val gprWrite = !idu.io.S && (idu.io.R || idu.io.I || idu.io.U || idu.io.J || idu.io.mem_load || idu.io.csr)
    val normalWriteback = ifuFire && !memOperation && gprWrite

    val memRd = RegInit(0.U(5.W))
    memRd := Mux(issueMemory, idu.io.rdaddr, memRd)

    ifu.io.out.ready := !lsu.io.busy
    ifu.io.jump := ifuFire && jump
    ifu.io.jumptarget := jumpTarget
    ifu.io.stall := lsu.io.busy
    ifu.io.cacheRespValid := icache.io.cpuRespValid
    ifu.io.cacheRespInst := icache.io.cpuRespInst
    icache.io.cpuReq := ifu.io.cacheReq
    icache.io.cpuAddr := ifu.io.cacheAddr

    idu.io.inst := ifuInst
    idu.io.pc := ifuPc.asUInt
    idu.io.rs1data := gprRs1Data
    idu.io.rs2data := gprRs2Data

    exu.io.a := idu.io.alu_a
    exu.io.b := idu.io.alu_b
    exu.io.add := idu.io.alu_add
    exu.io.lh20 := idu.io.alu_lh20
    exu.io.sub := idu.io.alu_sub
    exu.io.slt := idu.io.alu_slt
    exu.io.sltu := idu.io.alu_sltu
    exu.io.xor := idu.io.alu_xor
    exu.io.or := idu.io.alu_or
    exu.io.and := idu.io.alu_and
    exu.io.sll := idu.io.alu_sll
    exu.io.srl := idu.io.alu_srl
    exu.io.sra := idu.io.alu_sra
    exu.io.mul := idu.io.alu_mul
    exu.io.mulh := idu.io.alu_mulh
    exu.io.mulsu := idu.io.alu_mulsu
    exu.io.mulu := idu.io.alu_mulu
    exu.io.div := idu.io.alu_div
    exu.io.divu := idu.io.alu_divu
    exu.io.rem := idu.io.alu_rem
    exu.io.remu := idu.io.alu_remu

    lsu.io.exuResult := exuResult
    lsu.io.funct3 := ifuInst(14, 12)
    lsu.io.gprRs2Data := gprRs2Data
    lsu.io.load := issueMemory && idu.io.mem_load
    lsu.io.store := issueMemory && idu.io.S
    lsu.io.cacheRespValid := dcache.io.cpuRespValid
    lsu.io.cacheRespData := dcache.io.cpuRespData
    dcache.io.cpuReq := lsu.io.cacheReq
    dcache.io.cpuWrite := lsu.io.cacheWrite
    dcache.io.cpuAddr := lsu.io.cacheAddr
    dcache.io.cpuWData := lsu.io.cacheWData
    dcache.io.cpuWStrb := lsu.io.cacheWStrb

    wbu.io.exuresult := exuResult
    wbu.io.mem_rdata := lsu.io.loadData.asUInt
    wbu.io.pc := ifuPc
    wbu.io.mem_load := idu.io.mem_load
    wbu.io.J := idu.io.J
    wbu.io.jalr := idu.io.jalr
    wbu.io.csr := idu.io.csr
    wbu.io.csrval := csrReadValue

    gpr.io.rs1addr := idu.io.rs1addr
    gpr.io.rs2addr := idu.io.rs2addr
    gpr.io.we := normalWriteback || lsu.io.loadDone
    gpr.io.rdaddr := Mux(lsu.io.loadDone, memRd, idu.io.rdaddr)
    gpr.io.rddata := Mux(lsu.io.loadDone, lsu.io.loadData, wbu.io.writeback_data)

    csr.io.we := idu.io.csr_write && ifuFire && !memOperation
    csr.io.addr := idu.io.csraddr
    csr.io.op_val := gprRs1Data
    csr.io.clear := idu.io.csr_clear
    csr.io.set := idu.io.csr_set
    csr.io.write := idu.io.csr_write && ifuFire && !memOperation
    csr.io.pc := ifuPc.asUInt
    csr.io.ecall := idu.io.ecall && ifuFire && !memOperation
    csr.io.mret := idu.io.mret && ifuFire && !memOperation

    axiMaster.io.icache_axi <> icache.io.axi
    axiMaster.io.lsu_axi <> dcache.io.axi
    connectAxiMaster(io.pmem_axi, axiMaster.io.pmem_axi)

    io.inst := ifuInst
    io.pc := ifuPc.asUInt
  }

  private def connectAxiMaster(external: AXIMaster, internal: AXIMaster): Unit = {
    external.ar.arid := internal.ar.arid
    external.ar.araddr := internal.ar.araddr
    external.ar.arlen := internal.ar.arlen
    external.ar.arsize := internal.ar.arsize
    external.ar.arburst := internal.ar.arburst
    external.ar.arlock := internal.ar.arlock
    external.ar.arcache := internal.ar.arcache
    external.ar.arprot := internal.ar.arprot
    external.ar.arqos := internal.ar.arqos
    external.ar.arregion := internal.ar.arregion
    external.ar.arvalid := internal.ar.arvalid
    internal.ar.arready := external.ar.arready

    internal.r.rid := external.r.rid
    internal.r.rdata := external.r.rdata
    internal.r.rresp := external.r.rresp
    internal.r.rlast := external.r.rlast
    internal.r.rvalid := external.r.rvalid
    external.r.rready := internal.r.rready

    external.aw.awid := internal.aw.awid
    external.aw.awaddr := internal.aw.awaddr
    external.aw.awlen := internal.aw.awlen
    external.aw.awsize := internal.aw.awsize
    external.aw.awburst := internal.aw.awburst
    external.aw.awlock := internal.aw.awlock
    external.aw.awcache := internal.aw.awcache
    external.aw.awprot := internal.aw.awprot
    external.aw.awqos := internal.aw.awqos
    external.aw.awregion := internal.aw.awregion
    external.aw.awvalid := internal.aw.awvalid
    internal.aw.awready := external.aw.awready

    external.w.wdata := internal.w.wdata
    external.w.wstrb := internal.w.wstrb
    external.w.wlast := internal.w.wlast
    external.w.wvalid := internal.w.wvalid
    internal.w.wready := external.w.wready

    internal.b.bid := external.b.bid
    internal.b.bresp := external.b.bresp
    internal.b.bvalid := external.b.bvalid
    external.b.bready := internal.b.bready
  }
}
