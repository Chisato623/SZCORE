package SZCORE

import chisel3._
import chisel3.util._

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
    val idu = Module(new IDU)
    val exu = Module(new EXU)
    val mulDiv = Module(new MulDiv)
    val lsu = Module(new LSU)
    val gpr = Module(new GPR)
    val csr = Module(new CSR)

    // Five independently declared pipeline registers: fetch, IF/ID, ID/EX,
    // EX/MEM and MEM/WB.  A valid bit turns an entry into a harmless bubble.
    val fetch = RegInit(0.U.asTypeOf(new FetchReg))
    val ifId = RegInit(0.U.asTypeOf(new IFIDReg))
    val idEx = RegInit(0.U.asTypeOf(new IDEXReg))
    val exMem = RegInit(0.U.asTypeOf(new EXMEMReg))
    val memWb = RegInit(0.U.asTypeOf(new MEMWBReg))
    // Kept for the optional Verilator retirement trace injected by Generate.
    dontTouch(exMem.pc)
    dontTouch(memWb.pc)

    idu.io.inst := ifId.inst
    idu.io.pc := ifId.pc
    gpr.io.rs1addr := idu.io.rs1addr
    gpr.io.rs2addr := idu.io.rs2addr
    gpr.io.we := memWb.valid && memWb.regWrite && memWb.rd =/= 0.U
    gpr.io.rdaddr := memWb.rd
    gpr.io.rddata := memWb.value

    // WB-to-ID forwarding handles a register file read in the same clock as WB.
    val decodeRs1 = Mux(memWb.valid && memWb.regWrite && memWb.rd =/= 0.U &&
      memWb.rd === idu.io.rs1addr, memWb.value, gpr.io.rs1data)
    val decodeRs2 = Mux(memWb.valid && memWb.regWrite && memWb.rd =/= 0.U &&
      memWb.rd === idu.io.rs2addr, memWb.value, gpr.io.rs2data)
    idu.io.rs1data := decodeRs1
    idu.io.rs2data := decodeRs2

    val exMemForwardable = exMem.valid && exMem.regWrite && !exMem.memLoad && exMem.rd =/= 0.U
    val memWbForwardable = memWb.valid && memWb.regWrite && memWb.rd =/= 0.U
    val exRs1 = Mux(exMemForwardable && exMem.rd === idEx.rs1addr, exMem.writebackValue,
      Mux(memWbForwardable && memWb.rd === idEx.rs1addr, memWb.value, idEx.rs1))
    val exRs2 = Mux(exMemForwardable && exMem.rd === idEx.rs2addr, exMem.writebackValue,
      Mux(memWbForwardable && memWb.rd === idEx.rs2addr, memWb.value, idEx.rs2))

    val exA = Mux(idEx.auipc || idEx.jal || idEx.branch, idEx.pc.asSInt, exRs1)
    val exB = Mux(idEx.regWrite && !idEx.memStore && !idEx.branch && !idEx.jal && !idEx.rType,
      idEx.imm, Mux(idEx.jal || idEx.branch || idEx.auipc || idEx.memLoad || idEx.memStore,
        idEx.imm, exRs2))
    exu.io.a := exA
    exu.io.b := exB
    exu.io.add := idEx.aluAdd
    exu.io.lh20 := idEx.aluLh20
    exu.io.sub := idEx.aluSub
    exu.io.slt := idEx.aluSlt
    exu.io.sltu := idEx.aluSltu
    exu.io.xor := idEx.aluXor
    exu.io.or := idEx.aluOr
    exu.io.and := idEx.aluAnd
    exu.io.sll := idEx.aluSll
    exu.io.srl := idEx.aluSrl
    exu.io.sra := idEx.aluSra

    val mulDivActive = idEx.valid && idEx.muldiv
    val executeStall = mulDivActive && !mulDiv.io.done
    mulDiv.io.start := mulDivActive && !mulDiv.io.busy && !mulDiv.io.done
    mulDiv.io.funct3 := idEx.funct3
    mulDiv.io.a := exA
    mulDiv.io.b := exB
    val exAluResult = Mux(idEx.muldiv, mulDiv.io.result, exu.io.result)

    val branchTaken = MuxLookup(idEx.funct3, false.B)(Seq(
      "b000".U -> (exRs1 === exRs2),
      "b001".U -> (exRs1 =/= exRs2),
      "b100".U -> (exRs1 < exRs2),
      "b101".U -> (exRs1 >= exRs2),
      "b110".U -> (exRs1.asUInt < exRs2.asUInt),
      "b111".U -> (exRs1.asUInt >= exRs2.asUInt)
    ))

    csr.io.addr := Mux(memWb.valid && memWb.csr, memWb.csrAddr, idEx.csrAddr)
    csr.io.op_val := Mux(memWb.valid && memWb.csr, memWb.csrOpValue, exRs1)
    csr.io.write := memWb.valid && memWb.csr && memWb.csrWrite
    csr.io.set := memWb.valid && memWb.csr && memWb.csrSet
    csr.io.clear := memWb.valid && memWb.csr && memWb.csrClear
    csr.io.we := memWb.valid && memWb.csr && (memWb.csrWrite || memWb.csrSet || memWb.csrClear)
    // Trap redirects must observe mtvec/mepc in EX; normal CSR writes still retire in WB.
    csr.io.ecall := idEx.valid && idEx.ecall
    csr.io.mret := idEx.valid && idEx.mret
    csr.io.pc := idEx.pc

    val redirect = idEx.valid && !executeStall &&
      (idEx.jal || idEx.jalr || idEx.ecall || idEx.mret || (idEx.branch && branchTaken))
    val branchTarget = (idEx.pc.asSInt + idEx.imm).asUInt
    val jalrTarget = ((exRs1 + idEx.imm).asUInt & "hfffffffe".U)
    val redirectTarget = Mux(idEx.ecall || idEx.mret, csr.io.readval.asUInt,
      Mux(idEx.jalr, jalrTarget, branchTarget))
    val exWritebackValue = Mux(idEx.jal || idEx.jalr, (idEx.pc + 4.U).asSInt,
      Mux(idEx.csr, csr.io.readval, exAluResult))

    val memOperation = exMem.memLoad || exMem.memStore
    lsu.io.exuResult := exMem.aluResult
    lsu.io.funct3 := exMem.funct3
    lsu.io.gprRs2Data := exMem.storeData
    lsu.io.load := exMem.valid && exMem.memLoad && !lsu.io.busy
    lsu.io.store := exMem.valid && exMem.memStore && !lsu.io.busy
    lsu.io.cacheRespValid := daccess_rvalid || daccess_wresp
    lsu.io.cacheRespData := daccess_rdata
    daccess_ren := Mux(lsu.io.cacheReq && !lsu.io.cacheWrite, "b1111".U, 0.U)
    daccess_wen := Mux(lsu.io.cacheReq && lsu.io.cacheWrite, lsu.io.cacheWStrb, 0.U)
    daccess_addr := lsu.io.cacheAddr
    daccess_wdata := lsu.io.cacheWData

    // The blocking cache freezes EX/MEM and all younger stages until it replies.
    val memReady = !exMem.valid || !memOperation || lsu.io.loadDone || lsu.io.storeDone
    val memoryStall = exMem.valid && memOperation && !memReady

    val opcode = ifId.inst(6, 0)
    val decodeUsesRs1 = opcode === "b0110011".U || opcode === "b0010011".U ||
      opcode === "b0000011".U || opcode === "b0100011".U || opcode === "b1100011".U ||
      opcode === "b1100111".U || (opcode === "b1110011".U && ifId.inst(14, 12) =/= 0.U)
    val decodeUsesRs2 = opcode === "b0110011".U || opcode === "b0100011".U || opcode === "b1100011".U
    val loadUseStall = ifId.valid && idEx.valid && idEx.memLoad && idEx.rd =/= 0.U &&
      ((decodeUsesRs1 && idEx.rd === idu.io.rs1addr) || (decodeUsesRs2 && idEx.rd === idu.io.rs2addr))

    // CSR and trap instructions are deliberately serialised until they retire.
    val decodeSystem = idu.io.csr || idu.io.ecall || idu.io.mret
    val systemInFlight = (idEx.valid && (idEx.csr || idEx.ecall || idEx.mret)) ||
      (exMem.valid && exMem.csr) || (memWb.valid && memWb.csr)
    val csrStall = ifId.valid && decodeSystem && (idEx.valid || exMem.valid || memWb.valid)
    val decodeAdvance = ifId.valid && !memoryStall && !executeStall && !loadUseStall &&
      !csrStall && !systemInFlight && !redirect

    val fetchAllowed = !fetch.pending && !fetch.responseValid && !ifId.valid && !memoryStall && !executeStall &&
      !systemInFlight && !redirect
    ifetch_req := fetchAllowed
    ifetch_addr := fetch.pc

    when(memoryStall || executeStall) {
      // Hold every pipeline register that can feed the blocked stage.
    }.otherwise {
      memWb.valid := exMem.valid && !exMem.memStore
      memWb.pc := exMem.pc
      memWb.value := Mux(exMem.memLoad, lsu.io.loadData, exMem.writebackValue)
      memWb.rd := exMem.rd
      memWb.regWrite := exMem.regWrite
      memWb.csr := exMem.csr
      memWb.csrWrite := exMem.csrWrite
      memWb.csrSet := exMem.csrSet
      memWb.csrClear := exMem.csrClear
      memWb.csrAddr := exMem.csrAddr
      memWb.csrOpValue := exMem.csrOpValue

      exMem.valid := idEx.valid
      exMem.pc := idEx.pc
      exMem.aluResult := exAluResult
      exMem.writebackValue := exWritebackValue
      exMem.storeData := exRs2
      exMem.rd := idEx.rd
      exMem.funct3 := idEx.funct3
      exMem.regWrite := idEx.regWrite
      exMem.memLoad := idEx.memLoad
      exMem.memStore := idEx.memStore
      exMem.csr := idEx.csr
      exMem.csrWrite := idEx.csrWrite
      exMem.csrSet := idEx.csrSet
      exMem.csrClear := idEx.csrClear
      exMem.csrAddr := idEx.csrAddr
      exMem.csrOpValue := exRs1

      when(redirect || loadUseStall) {
        idEx.valid := false.B
      }.otherwise {
        idEx.valid := decodeAdvance
        idEx.pc := ifId.pc
        idEx.rs1 := decodeRs1
        idEx.rs2 := decodeRs2
        idEx.imm := idu.io.alu_b
        idEx.rd := idu.io.rdaddr
        idEx.rs1addr := idu.io.rs1addr
        idEx.rs2addr := idu.io.rs2addr
        idEx.funct3 := ifId.inst(14, 12)
        idEx.regWrite := !idu.io.S && (idu.io.R || idu.io.I || idu.io.U || idu.io.J || idu.io.mem_load || idu.io.csr)
        idEx.rType := idu.io.R
        idEx.memLoad := idu.io.mem_load
        idEx.memStore := idu.io.S
        idEx.branch := idu.io.B
        idEx.jal := idu.io.J
        idEx.jalr := idu.io.jalr
        idEx.auipc := idu.io.auipc
        idEx.csr := idu.io.csr
        idEx.csrWrite := idu.io.csr_write
        idEx.csrSet := idu.io.csr_set
        idEx.csrClear := idu.io.csr_clear
        idEx.csrAddr := idu.io.csraddr
        idEx.ecall := idu.io.ecall
        idEx.mret := idu.io.mret
        idEx.muldiv := idu.io.muldiv
        idEx.aluAdd := idu.io.alu_add
        idEx.aluLh20 := idu.io.alu_lh20
        idEx.aluSub := idu.io.alu_sub
        idEx.aluSlt := idu.io.alu_slt
        idEx.aluSltu := idu.io.alu_sltu
        idEx.aluXor := idu.io.alu_xor
        idEx.aluOr := idu.io.alu_or
        idEx.aluAnd := idu.io.alu_and
        idEx.aluSll := idu.io.alu_sll
        idEx.aluSrl := idu.io.alu_srl
        idEx.aluSra := idu.io.alu_sra
      }

      when(redirect) {
        ifId.valid := false.B
      }.elsewhen(loadUseStall || csrStall) {
        ifId := ifId
      }.elsewhen(fetch.responseValid) {
        ifId.valid := true.B
        ifId.pc := fetch.responsePc
        ifId.inst := fetch.responseInst
      }.elsewhen(decodeAdvance) {
        ifId.valid := false.B
      }
    }

    // A redirect cannot cancel an ICache request, so its response is discarded.
    when(redirect) {
      fetch.pc := redirectTarget
      fetch.responseValid := false.B
      when(fetch.pending) { fetch.dropResponse := true.B }
    }.elsewhen(ifetch_valid && fetch.pending) {
      fetch.pending := false.B
      when(!fetch.dropResponse) {
        fetch.responseValid := true.B
        fetch.responsePc := fetch.pc - 4.U
        fetch.responseInst := ifetch_inst
      }
      fetch.dropResponse := false.B
    }.elsewhen(fetch.responseValid && !memoryStall && !executeStall && !systemInFlight && !ifId.valid) {
      fetch.responseValid := false.B
    }.elsewhen(fetchAllowed) {
      fetch.pending := true.B
      fetch.pc := fetch.pc + 4.U
    }
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
