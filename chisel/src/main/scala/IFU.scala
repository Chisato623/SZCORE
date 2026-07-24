package SZCORE

import chisel3._
import chisel3.util._

class IFUMessage extends Bundle {
  val inst = UInt(32.W)
  val pc = UInt(32.W)
}

/**
  * Decoupled instruction front end.
  *
  * The ICache interface intentionally keeps the legacy req/valid protocol.
  * A four-entry FIFO absorbs hit responses while decode consumes older
  * instructions.  A redirect drops queued entries and drains any old response
  * before fetching from the target PC.
  */
class IFU extends Module {
  private val fifoDepth = 4

  val io = IO(new Bundle {
    val out = Decoupled(new IFUMessage)
    val cacheReq = Output(Bool())
    val cacheAddr = Output(UInt(32.W))
    val cacheRespValid = Input(Bool())
    val cacheRespInst = Input(UInt(32.W))
    val jump = Input(Bool())
    val jumptarget = Input(UInt(32.W))
    val stall = Input(Bool())
  })

  val fetchPc = RegInit(0.U(32.W))
  val responsePc = RegInit(0.U(32.W))
  val inFlight = RegInit(false.B)
  val draining = RegInit(false.B)
  val fifoPc = Reg(Vec(fifoDepth, UInt(32.W)))
  val fifoInst = Reg(Vec(fifoDepth, UInt(32.W)))
  val head = RegInit(0.U(2.W))
  val tail = RegInit(0.U(2.W))
  val count = RegInit(0.U(3.W))

  val responseFire = io.cacheRespValid && inFlight
  val fetchSlotAvailable = !inFlight || responseFire
  val requestFire = fetchSlotAvailable && !io.stall && !io.jump && !draining && count < fifoDepth.U
  val dequeue = io.out.valid && io.out.ready

  io.cacheReq := requestFire
  io.cacheAddr := fetchPc
  io.out.valid := count =/= 0.U
  io.out.bits.pc := fifoPc(head)
  io.out.bits.inst := fifoInst(head)

  val enqueue = !io.jump && !draining && responseFire
  val normalCount = Mux(
    responseFire && !dequeue,
    count + 1.U,
    Mux(!responseFire && dequeue, count - 1.U, count)
  )

  fetchPc := Mux(io.jump, io.jumptarget, Mux(!draining && requestFire, fetchPc + 4.U, fetchPc))
  responsePc := Mux(
    io.jump && (!inFlight || responseFire),
    io.jumptarget,
    Mux(draining && responseFire, fetchPc, Mux(!draining && responseFire, responsePc + 4.U, responsePc))
  )
  inFlight := Mux(
    io.jump,
    inFlight && !responseFire,
    Mux(draining, Mux(responseFire, false.B, inFlight), requestFire || (inFlight && !responseFire))
  )
  draining := Mux(io.jump, inFlight && !responseFire, Mux(draining && responseFire, false.B, draining))
  head := Mux(io.jump, 0.U, Mux(!draining && dequeue, head + 1.U, head))
  tail := Mux(io.jump, 0.U, Mux(enqueue, tail + 1.U, tail))
  count := Mux(io.jump, 0.U, Mux(draining, count, normalCount))

  for (index <- 0 until fifoDepth) {
    val writeSlot = enqueue && tail === index.U
    fifoPc(index) := Mux(writeSlot, responsePc, fifoPc(index))
    fifoInst(index) := Mux(writeSlot, io.cacheRespInst, fifoInst(index))
  }
}
