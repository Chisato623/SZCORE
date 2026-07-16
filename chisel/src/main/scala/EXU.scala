package SZCORE
import chisel3.util._
import chisel3._
class EXU extends Module {
  val io = IO(new Bundle {
    val a      = Input(SInt(32.W))
    val b      = Input(SInt(32.W))
    val add    = Input(Bool())
    val lh20   = Input(Bool())
    val sub    = Input(Bool())
    val slt    = Input(Bool())
    val sltu   = Input(Bool())
    val xor    = Input(Bool())
    val or     = Input(Bool())
    val and    = Input(Bool())
    val sll    = Input(Bool())
    val srl    = Input(Bool())
    val sra    = Input(Bool())
    val mul    = Input(Bool())
    val mulh   = Input(Bool())
    val mulsu  = Input(Bool())
    val mulu   = Input(Bool())
    val div    = Input(Bool())
    val divu   = Input(Bool())
    val rem    = Input(Bool())
    val remu   = Input(Bool())
    val result = Output(SInt(32.W))

  })

  /** Restoring unsigned remainder, unrolled into 32 shift/compare/subtract stages. */
  private def unsignedRemainder(dividend: UInt, divisor: UInt): UInt = {
    val extendedDivisor = Cat(0.U(1.W), divisor)
    var remainder = 0.U(33.W)

    for (bit <- 31 to 0 by -1) {
      val shifted = Cat(remainder(31, 0), dividend(bit))
      remainder = Mux(shifted >= extendedDivisor, shifted - extendedDivisor, shifted)
    }

    remainder(31, 0)
  }

  private def signedRemainder(dividend: SInt, divisor: SInt): SInt = {
    val dividendBits = dividend.asUInt
    val divisorBits = divisor.asUInt
    val dividendAbs = Mux(dividend < 0.S(32.W), (~dividendBits + 1.U)(31, 0), dividendBits)
    val divisorAbs = Mux(divisor < 0.S(32.W), (~divisorBits + 1.U)(31, 0), divisorBits)
    val magnitude = unsignedRemainder(dividendAbs, divisorAbs)
    val signedMagnitude = Mux(
      dividend < 0.S(32.W),
      (~magnitude + 1.U)(31, 0),
      magnitude
    ).asSInt

    Mux(divisor === 0.S(32.W), dividend, signedMagnitude)
  }

  io.result := MuxCase(
    io.a + io.b,
    Seq(
      io.add  -> (io.a + io.b),
      io.lh20 -> io.b,
      io.sub  -> (io.a - io.b),
      io.slt  -> (Cat(Fill(31, 0.U), (io.a < io.b))).asSInt,
      io.sltu -> (Cat(Fill(31, 0.U), (io.a.asUInt < io.b.asUInt))).asSInt,
      io.xor  -> (io.a ^ io.b),
      io.or   -> (io.a | io.b),
      io.and  -> (io.a & io.b),
      io.sll  -> (io.a.asUInt << io.b(4, 0).asUInt).asSInt,
      io.srl  -> (io.a.asUInt >> io.b(4, 0).asUInt).asSInt,
      io.sra  -> (io.a >> io.b(4, 0).asUInt),
      io.mul  -> (io.a * io.b),
      io.mulh -> (io.a.asSInt * io.b.asSInt)(63, 32).asSInt,
    io.mulsu -> (io.a.asSInt * io.b.asUInt)(63, 32).asSInt,
        io.mulu -> (io.a.asUInt * io.b.asUInt)(63, 32).asSInt,
      io.div  -> (io.a / io.b),
        io.divu -> (io.a.asUInt / io.b.asUInt).asSInt,
        io.rem  -> signedRemainder(io.a, io.b),
        io.remu -> Mux(
          io.b === 0.S(32.W),
          io.a,
          unsignedRemainder(io.a.asUInt, io.b.asUInt).asSInt
        )
    )
  )
}
