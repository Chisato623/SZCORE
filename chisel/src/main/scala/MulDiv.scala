package SZCORE

import chisel3._
import chisel3.util._

/** RV32M execution unit with a fixed 32-cycle completion latency. */
class MulDiv extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val funct3 = Input(UInt(3.W))
    val a = Input(SInt(32.W))
    val b = Input(SInt(32.W))
    val busy = Output(Bool())
    val done = Output(Bool())
    val result = Output(SInt(32.W))
  })

  val running = RegInit(false.B)
  val iteration = RegInit(0.U(5.W))
  val donePulse = RegInit(false.B)
  val resultReg = RegInit(0.U(32.W))

  val opReg = RegInit(0.U(3.W))
  val multiplyReg = RegInit(false.B)
  val negateQuotientReg = RegInit(false.B)
  val negateRemainderReg = RegInit(false.B)
  val divideByZeroReg = RegInit(false.B)
  val signedOverflowReg = RegInit(false.B)
  val operandAReg = RegInit(0.U(32.W))

  val multiplicandReg = RegInit(0.U(64.W))
  val multiplierReg = RegInit(0.U(32.W))
  val productReg = RegInit(0.U(64.W))

  val dividendReg = RegInit(0.U(32.W))
  val divisorReg = RegInit(0.U(32.W))
  val remainderReg = RegInit(0.U(33.W))
  val quotientReg = RegInit(0.U(32.W))

  val aU = io.a.asUInt
  val bU = io.b.asUInt
  val aMagnitude = Mux(aU(31), (~aU + 1.U)(31, 0), aU)
  val bMagnitude = Mux(bU(31), (~bU + 1.U)(31, 0), bU)
  val multiply = io.funct3 <= 3.U
  val signedMultiply = io.funct3 =/= 3.U
  val signedDivide = io.funct3 === 4.U || io.funct3 === 6.U

  donePulse := false.B

  when(!running) {
    when(io.start) {
      running := true.B
      iteration := 0.U
      opReg := io.funct3
      multiplyReg := multiply
      operandAReg := aU
      productReg := 0.U
      quotientReg := 0.U
      remainderReg := 0.U

      when(multiply) {
        val operandANegative = signedMultiply && aU(31)
        val operandBNegative = (io.funct3 === 0.U || io.funct3 === 1.U) && bU(31)
        multiplicandReg := Cat(0.U(32.W), Mux(operandANegative, aMagnitude, aU))
        multiplierReg := Mux(operandBNegative, bMagnitude, bU)
        negateQuotientReg := operandANegative ^ operandBNegative
        negateRemainderReg := false.B
        divideByZeroReg := false.B
        signedOverflowReg := false.B
        dividendReg := 0.U
        divisorReg := 0.U
      }.otherwise {
        val aNegative = signedDivide && aU(31)
        val bNegative = signedDivide && bU(31)
        dividendReg := Mux(aNegative, aMagnitude, aU)
        divisorReg := Mux(bNegative, bMagnitude, bU)
        multiplicandReg := 0.U
        multiplierReg := 0.U
        negateQuotientReg := aNegative ^ bNegative
        negateRemainderReg := aNegative
        divideByZeroReg := bU === 0.U
        signedOverflowReg := signedDivide && aU === "h80000000".U && bU === "hffffffff".U
      }
    }
  }.otherwise {
    when(multiplyReg) {
      val productNext = productReg + Mux(multiplierReg(0), multiplicandReg, 0.U(64.W))
      productReg := productNext
      multiplicandReg := multiplicandReg << 1
      multiplierReg := multiplierReg >> 1

      when(iteration === 31.U) {
        val signedProduct = Mux(negateQuotientReg, (~productNext + 1.U)(63, 0), productNext)
        resultReg := MuxLookup(opReg, signedProduct(31, 0))(Seq(
          0.U -> signedProduct(31, 0),
          1.U -> signedProduct(63, 32),
          2.U -> signedProduct(63, 32),
          3.U -> productNext(63, 32)
        ))
        running := false.B
        donePulse := true.B
      }.otherwise {
        iteration := iteration + 1.U
      }
    }.otherwise {
      val shiftedRemainder = Cat(remainderReg(31, 0), dividendReg(31))
      val divisorExtended = Cat(0.U(1.W), divisorReg)
      val subtract = shiftedRemainder >= divisorExtended
      val remainderNext = Mux(subtract, shiftedRemainder - divisorExtended, shiftedRemainder)
      val quotientNext = Cat(quotientReg(30, 0), subtract)
      remainderReg := remainderNext
      quotientReg := quotientNext
      dividendReg := dividendReg << 1

      when(iteration === 31.U) {
        val signedQuotient = Mux(negateQuotientReg, (~quotientNext + 1.U)(31, 0), quotientNext)
        val signedRemainder = Mux(negateRemainderReg, (~remainderNext(31, 0) + 1.U)(31, 0), remainderNext(31, 0))
        val quotientResult = Mux(divideByZeroReg, "hffffffff".U,
          Mux(signedOverflowReg, operandAReg, signedQuotient))
        val remainderResult = Mux(divideByZeroReg, operandAReg,
          Mux(signedOverflowReg, 0.U, signedRemainder))
        resultReg := Mux(opReg === 4.U || opReg === 5.U, quotientResult, remainderResult)
        running := false.B
        donePulse := true.B
      }.otherwise {
        iteration := iteration + 1.U
      }
    }
  }

  io.busy := running
  io.done := donePulse
  io.result := resultReg.asSInt
}
