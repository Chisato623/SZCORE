import "DPI-C" function int pmem_read(input int raddr);
import "DPI-C" function void pmem_write(
  input int  waddr,
  input int  wdata,
  input byte wmask
);
module DPIPmem (
    // Clock
    input clock,

    // IFU Interface
    input      [31:0] ifu_pc,
    input             ifu_reqvalid,
    output reg [31:0] ifu_inst,
    output            ifu_respvalid,

    // LSU Interface
    input      [31:0] lsu_raddr,
    input      [31:0] lsu_waddr,
    input      [31:0] lsu_wdata,
    input      [ 7:0] lsu_wmask,
    input             lsu_wen,
    input             lsu_reqvalid,
    output reg [31:0] lsu_rdata,
    output            lsu_respvalid
);
  always @(posedge clock) begin
    ifu_inst <= (ifu_reqvalid) ? pmem_read(ifu_pc) : 32'b0;
    ifu_respvalid <= ifu_reqvalid;
  end
  always @(posedge clock) begin
    lsu_rdata <= (lsu_reqvalid && !lsu_wen) ? pmem_read(lsu_raddr) : 32'b0;
    if (lsu_reqvalid && lsu_wen) begin
      pmem_write(lsu_waddr, lsu_wdata, lsu_wmask);
    end
    lsu_respvalid <= lsu_reqvalid;
  end


endmodule
