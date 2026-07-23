package SZCORE

import chisel3._
import _root_.circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._



object Generate extends App {
  private val outputDir = Paths.get("rtl")
  private val verilogHeader = "`timescale 1ns / 1ps\n\n`include \"defines.vh\"\n\n"
  private val runTraceBlock = """
`ifdef RUN_TRACE
  // Pipeline trace points.  WB observes the instruction retiring from MEM/WB;
  // memory observes a store only while the LSU issues its DCache request.
  wire [31:0] debug_wb_pc    /* verilator public */ ;
  wire        debug_wb_rf_we /* verilator public */ ;
  wire [4:0]  debug_wb_rf_wR /* verilator public */ ;
  wire [31:0] debug_wb_rf_wD /* verilator public */ ;
  wire [31:0] debug_mem_pc    /* verilator public */ ;
  wire [3:0]  debug_mem_we    /* verilator public */ ;
  wire [31:0] debug_mem_waddr /* verilator public */ ;
  wire [31:0] debug_mem_wdata /* verilator public */ ;
  wire        trace_wb_valid;
  wire        trace_mem_store;

  assign trace_wb_valid = memWb_valid;
  assign trace_mem_store = exMem_valid & exMem_memStore & (|daccess_wen);
  assign debug_wb_pc = trace_wb_valid ? memWb_pc : 32'h0;
  assign debug_wb_rf_we = trace_wb_valid & memWb_regWrite & (|memWb_rd);
  assign debug_wb_rf_wR = memWb_rd;
  assign debug_wb_rf_wD = memWb_value;
  assign debug_mem_pc = trace_mem_store ? exMem_pc : 32'h0;
  assign debug_mem_we = trace_mem_store ? daccess_wen : 4'h0;
  assign debug_mem_waddr = daccess_addr;
  assign debug_mem_wdata = daccess_wdata;
`endif
"""

  ChiselStage.emitSystemVerilog(
    new CpuTop,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl", "-disable-all-randomization", "-strip-debug-info"),
  )

  private def addVerilogHeader(path: Path): Unit = {
    val contents = Files.readString(path, StandardCharsets.UTF_8)
    if (!contents.startsWith(verilogHeader)) {
      Files.writeString(path, verilogHeader + contents, StandardCharsets.UTF_8)
    }
  }

  private def addRunTraceSupport(path: Path): Unit = {
    if (path.getFileName.toString == "cpu_core.sv") {
      var contents = Files.readString(path, StandardCharsets.UTF_8)
      contents = contents
        .replace("output        ifetch_req,", "output        ifetch_req /* verilator public */,")
        .replace("output [31:0] ifetch_addr,", "output [31:0] ifetch_addr /* verilator public */,")
        .replace("input         ifetch_valid,", "input         ifetch_valid /* verilator public */,")
      if (!contents.contains("debug_wb_pc /* verilator public */")) {
        val endmodule = contents.lastIndexOf("\nendmodule")
        require(endmodule >= 0, "cpu_core.sv has no terminating endmodule")
        contents = contents.patch(endmodule, "\n" + runTraceBlock, 0)
      }
      Files.writeString(path, contents, StandardCharsets.UTF_8)
    }
  }

  val outputFiles = Files.list(outputDir)
  try {
    outputFiles.iterator().asScala
      .filter(path => Files.isRegularFile(path) && path.toString.endsWith(".sv"))
      .foreach { path =>
        addVerilogHeader(path)
        addRunTraceSupport(path)
      }
  } finally {
    outputFiles.close()
  }
}

object GenerateTopVerify extends App {
  ChiselStage.emitSystemVerilog(
    new CpuTop,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl/topverify", "-disable-all-randomization", "-strip-debug-info")
  )
}
