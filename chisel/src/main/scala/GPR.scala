package SZCORE
import chisel3.util._
import chisel3._
class GPR extends Module {
  val io = IO(new Bundle {
    val rs1addr = Input(UInt(5.W))
    val rs2addr = Input(UInt(5.W))
    val rdaddr  = Input(UInt(5.W))
    val rs1data = Output(SInt(32.W))
    val rs2data = Output(SInt(32.W))
    val rddata  = Input(SInt(32.W))
    val a0val   = Output(SInt(32.W))
    val we      = Input(Bool())
  })

  val registers = RegInit(VecInit(Seq.fill(32)(0.S(32.W))))

  io.rs1data := registers(io.rs1addr)
  io.rs2data := registers(io.rs2addr)
  io.a0val   := registers(10.U)
  when(io.we && io.rdaddr =/= 0.U) {
    registers(io.rdaddr) := io.rddata
  }
}
