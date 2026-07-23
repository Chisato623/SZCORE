package SZCORE

import chisel3._
import chisel3.util._

/**
  * RV32M execution unit with a fixed 32-cycle completion latency.
  *
  * The arithmetic result is captured when the instruction is accepted.  The
  * fixed wait state gives the single-issue core an unambiguous busy/done
  * protocol and prevents a malformed iterative state from stalling the CPU.
  */
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
  val remainingCycles = RegInit(0.U(6.W))
  val donePulse = RegInit(false.B)
  val resultReg = RegInit(0.S(32.W))

  val aU = io.a.asUInt
  val bU = io.b.asUInt
  val signedProduct = (io.a * io.b).asUInt
  val signedUnsignedProduct = (io.a * Cat(0.U(1.W), bU).asSInt).asUInt
  val unsignedProduct = aU * bU
  val aNegative = aU(31)
  val bNegative = bU(31)
  val aMagnitude = Mux(aNegative, (~aU + 1.U)(31, 0), aU)
  val bMagnitude = Mux(bNegative, (~bU + 1.U)(31, 0), bU)
  val signedQuotientMagnitude = aMagnitude / bMagnitude
  val signedRemainderMagnitude = aMagnitude % bMagnitude
  val signedQuotient = Mux(
    aNegative ^ bNegative,
    (~signedQuotientMagnitude + 1.U)(31, 0),
    signedQuotientMagnitude
  )
  val signedRemainder = Mux(
    aNegative,
    (~signedRemainderMagnitude + 1.U)(31, 0),
    signedRemainderMagnitude
  )
  val signedOverflow = aU === "h80000000".U && bU === "hffffffff".U
  val divideByZero = bU === 0.U

  val resultAtStart = WireDefault(0.U(32.W))
  switch(io.funct3) {
    is(0.U) { resultAtStart := signedProduct(31, 0) }       // MUL
    is(1.U) { resultAtStart := signedProduct(63, 32) }      // MULH
    is(2.U) { resultAtStart := signedUnsignedProduct(63, 32) } // MULHSU
    is(3.U) { resultAtStart := unsignedProduct(63, 32) }    // MULHU
    is(4.U) { // DIV
      resultAtStart := Mux(divideByZero, "hffffffff".U,
        Mux(signedOverflow, aU, signedQuotient))
    }
    is(5.U) { // DIVU
      resultAtStart := Mux(divideByZero, "hffffffff".U, aU / bU)
    }
    is(6.U) { // REM
      resultAtStart := Mux(divideByZero, aU,
        Mux(signedOverflow, 0.U, signedRemainder))
    }
    is(7.U) { // REMU
      resultAtStart := Mux(divideByZero, aU, aU % bU)
    }
  }

  donePulse := false.B
  when(!running) {
    when(io.start) {
      resultReg := resultAtStart.asSInt
      remainingCycles := 32.U
      running := true.B
    }
  }.otherwise {
    when(remainingCycles === 1.U) {
      remainingCycles := 0.U
      running := false.B
      donePulse := true.B
    }.otherwise {
      remainingCycles := remainingCycles - 1.U
    }
  }

  io.busy := running
  io.done := donePulse
  io.result := resultReg
}
