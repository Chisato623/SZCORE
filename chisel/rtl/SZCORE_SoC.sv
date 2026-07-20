`timescale 1ns / 1ps

`include "defines.vh"

module SZCORESOC4(
    input  wire        fpga_clk,
    input  wire        fpga_rst,
    input  wire [23:0] sw,
    output wire [23:0] led,
    output wire [7:0]  dig_en,
    output wire [7:0]  dig_seg,
    input  wire        rx,
    output wire        tx
);

`ifdef RUN_TRACE
    wire sys_clk = fpga_clk;
    wire sys_rst = fpga_rst;
`else
    wire pll_clk1;
    wire pll_lock;
    wire sys_clk = pll_lock & pll_clk1;
    reg sys_rst;
    always @(posedge fpga_clk) sys_rst <= fpga_rst | !pll_lock;

    clk_wiz_0 U_clkgen (
        .clk_in1(fpga_clk),
        .locked(pll_lock),
        .clk_out1(pll_clk1)
    );
`endif

    wire [31:0] cpu_inst;
    wire [31:0] cpu_pc;

    // Shared AXI4 link between the cache hierarchy and the AXI BRAM slave.
    wire [3:0]  axi_awid;
    wire [31:0] axi_awaddr;
    wire [7:0]  axi_awlen;
    wire [2:0]  axi_awsize;
    wire [1:0]  axi_awburst;
    wire        axi_awvalid;
    wire        axi_awready;
    wire [31:0] axi_wdata;
    wire [3:0]  axi_wstrb;
    wire        axi_wlast;
    wire        axi_wvalid;
    wire        axi_wready;
    wire [3:0]  axi_bid;
    wire [1:0]  axi_bresp;
    wire        axi_bvalid;
    wire        axi_bready;
    wire [3:0]  axi_arid;
    wire [31:0] axi_araddr;
    wire [7:0]  axi_arlen;
    wire [2:0]  axi_arsize;
    wire [1:0]  axi_arburst;
    wire        axi_arvalid;
    wire        axi_arready;
    wire [3:0]  axi_rid;
    wire [31:0] axi_rdata;
    wire [1:0]  axi_rresp;
    wire        axi_rlast;
    wire        axi_rvalid;
    wire        axi_rready;

    wire [31:0] bram_awaddr;
    wire [7:0]  bram_awlen;
    wire [2:0]  bram_awsize;
    wire [1:0]  bram_awburst;
    wire        bram_awvalid;
    wire        bram_awready;
    wire [31:0] bram_wdata;
    wire [3:0]  bram_wstrb;
    wire        bram_wlast;
    wire        bram_wvalid;
    wire        bram_wready;
    wire [3:0]  bram_bid;
    wire [1:0]  bram_bresp;
    wire        bram_bvalid;
    wire        bram_bready;
    wire [31:0] bram_araddr;
    wire [7:0]  bram_arlen;
    wire [2:0]  bram_arsize;
    wire [1:0]  bram_arburst;
    wire        bram_arvalid;
    wire        bram_arready;
    wire [3:0]  bram_rid;
    wire [31:0] bram_rdata;
    wire [1:0]  bram_rresp;
    wire        bram_rlast;
    wire        bram_rvalid;
    wire        bram_rready;

    assign bram_awaddr  = axi_awaddr;
    assign bram_awlen   = axi_awlen;
    assign bram_awsize  = axi_awsize;
    assign bram_awburst = axi_awburst;
    assign bram_awvalid = axi_awvalid;
    assign axi_awready  = bram_awready;
    assign bram_wdata   = axi_wdata;
    assign bram_wstrb   = axi_wstrb;
    assign bram_wlast   = axi_wlast;
    assign bram_wvalid  = axi_wvalid;
    assign axi_wready   = bram_wready;
    assign axi_bid      = bram_bid;
    assign axi_bresp    = bram_bresp;
    assign axi_bvalid   = bram_bvalid;
    assign bram_bready  = axi_bready;
    assign bram_araddr  = axi_araddr;
    assign bram_arlen   = axi_arlen;
    assign bram_arsize  = axi_arsize;
    assign bram_arburst = axi_arburst;
    assign bram_arvalid = axi_arvalid;
    assign axi_arready  = bram_arready;
    assign axi_rid      = bram_rid;
    assign axi_rdata    = bram_rdata;
    assign axi_rresp    = bram_rresp;
    assign axi_rlast    = bram_rlast;
    assign axi_rvalid   = bram_rvalid;
    assign bram_rready  = axi_rready;

    bram_axi U_bram (
        .s_aclk(sys_clk),
        .s_aresetn(!sys_rst),
        .s_axi_awid(axi_awid),
        .s_axi_awaddr(bram_awaddr),
        .s_axi_awlen(bram_awlen),
        .s_axi_awsize(bram_awsize),
        .s_axi_awburst(bram_awburst),
        .s_axi_awvalid(bram_awvalid),
        .s_axi_awready(bram_awready),
        .s_axi_wdata(bram_wdata),
        .s_axi_wstrb(bram_wstrb),
        .s_axi_wlast(bram_wlast),
        .s_axi_wvalid(bram_wvalid),
        .s_axi_wready(bram_wready),
        .s_axi_bid(bram_bid),
        .s_axi_bresp(bram_bresp),
        .s_axi_bvalid(bram_bvalid),
        .s_axi_bready(bram_bready),
        .s_axi_arid(axi_arid),
        .s_axi_araddr(bram_araddr),
        .s_axi_arlen(bram_arlen),
        .s_axi_arsize(bram_arsize),
        .s_axi_arburst(bram_arburst),
        .s_axi_arvalid(bram_arvalid),
        .s_axi_arready(bram_arready),
        .s_axi_rid(bram_rid),
        .s_axi_rdata(bram_rdata),
        .s_axi_rresp(bram_rresp),
        .s_axi_rlast(bram_rlast),
        .s_axi_rvalid(bram_rvalid),
        .s_axi_rready(bram_rready)
    );

    SZCOREtop U_cpu (
        .io_cpu_clk(sys_clk),
        .io_cpu_rst(sys_rst),
        .io_pmem_axi_ar_arid(axi_arid),
        .io_pmem_axi_ar_araddr(axi_araddr),
        .io_pmem_axi_ar_arlen(axi_arlen),
        .io_pmem_axi_ar_arsize(axi_arsize),
        .io_pmem_axi_ar_arburst(axi_arburst),
        .io_pmem_axi_ar_arlock(),
        .io_pmem_axi_ar_arcache(),
        .io_pmem_axi_ar_arprot(),
        .io_pmem_axi_ar_arqos(),
        .io_pmem_axi_ar_arregion(),
        .io_pmem_axi_ar_arvalid(axi_arvalid),
        .io_pmem_axi_ar_arready(axi_arready),
        .io_pmem_axi_r_rid(axi_rid),
        .io_pmem_axi_r_rdata(axi_rdata),
        .io_pmem_axi_r_rresp(axi_rresp),
        .io_pmem_axi_r_rlast(axi_rlast),
        .io_pmem_axi_r_rvalid(axi_rvalid),
        .io_pmem_axi_r_rready(axi_rready),
        .io_pmem_axi_aw_awid(axi_awid),
        .io_pmem_axi_aw_awaddr(axi_awaddr),
        .io_pmem_axi_aw_awlen(axi_awlen),
        .io_pmem_axi_aw_awsize(axi_awsize),
        .io_pmem_axi_aw_awburst(axi_awburst),
        .io_pmem_axi_aw_awlock(),
        .io_pmem_axi_aw_awcache(),
        .io_pmem_axi_aw_awprot(),
        .io_pmem_axi_aw_awqos(),
        .io_pmem_axi_aw_awregion(),
        .io_pmem_axi_aw_awvalid(axi_awvalid),
        .io_pmem_axi_aw_awready(axi_awready),
        .io_pmem_axi_w_wdata(axi_wdata),
        .io_pmem_axi_w_wstrb(axi_wstrb),
        .io_pmem_axi_w_wlast(axi_wlast),
        .io_pmem_axi_w_wvalid(axi_wvalid),
        .io_pmem_axi_w_wready(axi_wready),
        .io_pmem_axi_b_bid(axi_bid),
        .io_pmem_axi_b_bresp(axi_bresp),
        .io_pmem_axi_b_bvalid(axi_bvalid),
        .io_pmem_axi_b_bready(axi_bready),
        .io_inst(cpu_inst),
        .io_pc(cpu_pc)
    );

    assign led = 24'b0;
    assign dig_en = 8'b0;
    assign dig_seg = 8'b0;
    assign tx = 1'b1;
endmodule
