package SZCORE

import chisel3._
import _root_.circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._



object Generate extends App {
  private val outputDir = Paths.get("rtl")
  private val verilogHeader = "`timescale 1ns / 1ps\n\n`include \"defines.vh\"\n\n"

  ChiselStage.emitSystemVerilog(
    new SZCOREtop,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl", "-disable-all-randomization", "-strip-debug-info"),
  )

  private def addVerilogHeader(path: Path): Unit = {
    val contents = Files.readString(path, StandardCharsets.UTF_8)
    if (!contents.startsWith(verilogHeader)) {
      Files.writeString(path, verilogHeader + contents, StandardCharsets.UTF_8)
    }
  }

  val outputFiles = Files.list(outputDir)
  try {
    outputFiles.iterator().asScala
      .filter(path => Files.isRegularFile(path) && path.toString.endsWith(".sv"))
      .foreach(addVerilogHeader)
  } finally {
    outputFiles.close()
  }
}

object GenerateTopVerify extends App {
  ChiselStage.emitSystemVerilog(
    new SZCOREtop,
    args = Array.empty,
    firtoolOpts = Array("--split-verilog", "-o=rtl/topverify", "-disable-all-randomization", "-strip-debug-info")
  )
}
