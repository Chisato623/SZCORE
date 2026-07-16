`timescale 1ns / 1ps

`include "defines.vh"

module SZCORESOC4(
    input  wire         fpga_clk,
    input  wire         fpga_rst,
    input  wire [23:0]  sw,
    output wire [23:0]  led,
    output wire [ 7:0]  dig_en,
    output wire [ 7:0]  dig_seg,    // {CA, CB, ..., CG, DP}
    input  wire         rx,
    output wire         tx
);

`ifdef RUN_TRACE
    wire sys_clk = fpga_clk;
    wire sys_rst = fpga_rst;
`else
    wire pll_clk1;
    wire pll_lock;
    wire sys_clk = pll_lock & pll_clk1;
    reg  sys_rst;
    always @(posedge fpga_clk) sys_rst <= fpga_rst | !pll_lock;

    clk_wiz_0 U_clkgen (
        .clk_in1    (fpga_clk),
        .locked     (pll_lock),
        .clk_out1   (pll_clk1)
    );
`endif

    wire [31:0] cpu_inst;
    wire [31:0] cpu_pc;
    wire        ifetch_req;
    wire [31:0] ifetch_addr;
    reg         ifetch_valid;
    wire [31:0] ifetch_inst;

    wire [3:0]  daccess_ren;
    wire [31:0] daccess_addr;
    reg         daccess_rvalid;
    wire [31:0] daccess_rdata;
    wire [3:0]  daccess_wen;
    wire [31:0] daccess_wdata;
    reg         daccess_wresp;

    // Both block RAMs have a one-cycle read latency.  Return valid/response
    // one system clock after the core issues the corresponding request.
    always @(posedge sys_clk) begin
        if (sys_rst) begin
            ifetch_valid  <= 1'b0;
            daccess_rvalid <= 1'b0;
            daccess_wresp <= 1'b0;
        end else begin
            ifetch_valid  <= ifetch_req;
            daccess_rvalid <= |daccess_ren;
            daccess_wresp <= |daccess_wen;
        end
    end

    IROM U_irom (
        .clka  (sys_clk),
        .addra (ifetch_addr[15:2]),
        .douta (ifetch_inst)
    );

    DRAM U_dram (
        .clka  (sys_clk),
        .wea   (daccess_wen),
        .addra (daccess_addr[16:2]),
        .dina  (daccess_wdata),
        .douta (daccess_rdata)
    );

    SZCOREtop U_cpu (
        .io_cpu_clk (sys_clk),
        .io_cpu_rst (sys_rst),
        .io_ifetch_req (ifetch_req),
        .io_ifetch_addr (ifetch_addr),
        .io_ifetch_valid (ifetch_valid),
        .io_ifetch_inst (ifetch_inst),
        .io_daccess_ren (daccess_ren),
        .io_daccess_addr (daccess_addr),
        .io_daccess_rvalid (daccess_rvalid),
        .io_daccess_rdata (daccess_rdata),
        .io_daccess_wen (daccess_wen),
        .io_daccess_wdata (daccess_wdata),
        .io_daccess_wresp (daccess_wresp),
        .io_inst    (cpu_inst),
        .io_pc      (cpu_pc)
    );

    assign led     = 24'b0;
    assign dig_en  = 8'b0;
    assign dig_seg = 8'b0;
    assign tx      = 1'b1;

endmodule
