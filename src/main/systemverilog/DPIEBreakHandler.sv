import "DPI-C" function bit ebreak(
  int inst,
  int a0val
);

module DPIEBreakHandler (
    input  [31:0] inst,
    input  [31:0] a0val,
    output        stop
);

  assign stop = ebreak(inst, a0val);
endmodule
