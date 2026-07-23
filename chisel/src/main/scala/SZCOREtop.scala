package SZCORE

import chisel3._

/**
  * Architectural CPU core.
  *
  * Its ports deliberately match the course template: instruction and data
  * accesses are expressed independently from the cache and AXI hierarchy.
  */
class CpuCore extends RawModule {
  override def desiredName: String = "cpu_core"

  val cpu_rst = IO(Input(Bool()))
  val cpu_clk = IO(Input(Clock()))
  val ifetch_req = IO(Output(Bool()))
  val ifetch_addr = IO(Output(UInt(32.W)))
  val ifetch_valid = IO(Input(Bool()))
  val ifetch_inst = IO(Input(UInt(32.W)))
  val daccess_ren = IO(Output(UInt(4.W)))
  val daccess_addr = IO(Output(UInt(32.W)))
  val daccess_rvalid = IO(Input(Bool()))
  val daccess_rdata = IO(Input(UInt(32.W)))
  val daccess_wen = IO(Output(UInt(4.W)))
  val daccess_wdata = IO(Output(UInt(32.W)))
  val daccess_wresp = IO(Input(Bool()))

  withClockAndReset(cpu_clk, cpu_rst) {
    val ifu = Module(new IFU)
    val idu = Module(new IDU)
    val exu = Module(new EXU)
    val mulDiv = Module(new MulDiv)
    val lsu = Module(new LSU)
    val wbu = Module(new WBU)
    val gpr = Module(new GPR)
    val csr = Module(new CSR)

    val ifuInst = ifu.io.out.bits.inst
    val ifuPc = ifu.io.out.bits.pc
    val ifuFire = ifu.io.out.valid && ifu.io.out.ready
    val memOperation = idu.io.mem_load || idu.io.S
    val issueMemory = ifuFire && memOperation
    val aluResult = Mux(idu.io.muldiv, mulDiv.io.result, exu.io.result)
    val jump = idu.io.dobranch || idu.io.J || idu.io.jalr || idu.io.ecall || idu.io.mret
    val gprWrite = !idu.io.S && (idu.io.R || idu.io.I || idu.io.U || idu.io.J || idu.io.mem_load || idu.io.csr)
    val memRd = RegInit(0.U(5.W))
    when(issueMemory) { memRd := idu.io.rdaddr }

    val mulDivActive = ifu.io.out.valid && idu.io.muldiv
    val mulDivStart = mulDivActive && !mulDiv.io.busy && !mulDiv.io.done
    ifu.io.out.ready := !lsu.io.busy && (!mulDivActive || mulDiv.io.done)
    ifu.io.jump := ifuFire && jump
    ifu.io.jumptarget := Mux(idu.io.mret, csr.io.readval, aluResult)
    ifu.io.stall := lsu.io.busy || (mulDivActive && !mulDiv.io.done)
    ifu.io.cacheRespValid := ifetch_valid
    ifu.io.cacheRespInst := ifetch_inst
    ifetch_req := ifu.io.cacheReq
    ifetch_addr := ifu.io.cacheAddr

    idu.io.inst := ifuInst
    idu.io.pc := ifuPc.asUInt
    idu.io.rs1data := gpr.io.rs1data
    idu.io.rs2data := gpr.io.rs2data

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

    mulDiv.io.start := mulDivStart
    mulDiv.io.funct3 := idu.io.muldiv_funct3
    mulDiv.io.a := idu.io.alu_a
    mulDiv.io.b := idu.io.alu_b

    lsu.io.exuResult := aluResult
    lsu.io.funct3 := ifuInst(14, 12)
    lsu.io.gprRs2Data := gpr.io.rs2data
    lsu.io.load := issueMemory && idu.io.mem_load
    lsu.io.store := issueMemory && idu.io.S
    lsu.io.cacheRespValid := daccess_rvalid || daccess_wresp
    lsu.io.cacheRespData := daccess_rdata
    daccess_ren := Mux(lsu.io.cacheReq && !lsu.io.cacheWrite, "b1111".U, 0.U)
    daccess_wen := Mux(lsu.io.cacheReq && lsu.io.cacheWrite, lsu.io.cacheWStrb, 0.U)
    daccess_addr := lsu.io.cacheAddr
    daccess_wdata := lsu.io.cacheWData

    wbu.io.exuresult := aluResult
    wbu.io.mem_rdata := lsu.io.loadData.asUInt
    wbu.io.pc := ifuPc
    wbu.io.mem_load := idu.io.mem_load
    wbu.io.J := idu.io.J
    wbu.io.jalr := idu.io.jalr
    wbu.io.csr := idu.io.csr
    wbu.io.csrval := csr.io.readval

    gpr.io.rs1addr := idu.io.rs1addr
    gpr.io.rs2addr := idu.io.rs2addr
    gpr.io.we := (ifuFire && !memOperation && gprWrite) || lsu.io.loadDone
    gpr.io.rdaddr := Mux(lsu.io.loadDone, memRd, idu.io.rdaddr)
    gpr.io.rddata := Mux(lsu.io.loadDone, lsu.io.loadData, wbu.io.writeback_data)

    csr.io.addr := idu.io.csraddr
    csr.io.op_val := gpr.io.rs1data
    csr.io.write := idu.io.csr_write && ifuFire && !memOperation
    csr.io.set := idu.io.csr_set
    csr.io.clear := idu.io.csr_clear
    csr.io.ecall := idu.io.ecall && ifuFire && !memOperation
    csr.io.mret := idu.io.mret && ifuFire && !memOperation
    csr.io.pc := ifuPc.asUInt
    csr.io.we := idu.io.csr_write && ifuFire && !memOperation
  }
}

/** Cache and AXI hierarchy surrounding the template-compatible CPU core. */
class CpuTop extends RawModule {
  override def desiredName: String = "cpu_top"

  val io = IO(new Bundle {
    val cpu_clk = Input(Clock())
    val cpu_rst = Input(Bool())
    val pmem_axi = new AXIMaster(AXIConfig())
    val inst = Output(UInt(32.W))
    val pc = Output(UInt(32.W))
  })

  withClockAndReset(io.cpu_clk, io.cpu_rst) {
    val U_core = Module(new CpuCore)
    val icache = Module(new ICache)
    val dcache = Module(new DCache)
    val axiMaster = Module(new AXI_Master)

    U_core.cpu_clk := io.cpu_clk
    U_core.cpu_rst := io.cpu_rst
    U_core.ifetch_valid := icache.io.cpuRespValid
    U_core.ifetch_inst := icache.io.cpuRespInst
    icache.io.cpuReq := U_core.ifetch_req
    icache.io.cpuAddr := U_core.ifetch_addr

    val daccessReq = U_core.daccess_ren.orR || U_core.daccess_wen.orR
    dcache.io.cpuReq := daccessReq
    dcache.io.cpuWrite := U_core.daccess_wen.orR
    dcache.io.cpuAddr := U_core.daccess_addr
    dcache.io.cpuWData := U_core.daccess_wdata
    dcache.io.cpuWStrb := U_core.daccess_wen
    U_core.daccess_rvalid := dcache.io.cpuRespValid
    U_core.daccess_rdata := dcache.io.cpuRespData
    U_core.daccess_wresp := dcache.io.cpuRespValid

    axiMaster.io.icache_axi <> icache.io.axi
    axiMaster.io.lsu_axi <> dcache.io.axi
    axiMaster.io.pmem_axi <> io.pmem_axi
    io.inst := 0.U
    io.pc := 0.U
  }
}
