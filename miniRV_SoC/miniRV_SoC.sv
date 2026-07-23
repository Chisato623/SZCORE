`timescale 1ns / 1ps

`include "defines.vh"

module miniRV_SoC(
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
    reg [1:0] sys_rst_sync;

    // Keep simulation and hardware reset behavior identical: all reset
    // consumers, including BRAM, see a clock-synchronous reset.
    always @(posedge sys_clk) begin
        if (fpga_rst)
            sys_rst_sync <= 2'b11;
        else
            sys_rst_sync <= {sys_rst_sync[0], 1'b0};
    end
    wire sys_rst = sys_rst_sync[1];
`else
    wire pll_clk1;
    wire pll_lock;
    // Do not gate a clock with PLL lock.  clk_out1 is already driven through
    // the clock wizard's BUFG; use lock only to hold the design in reset.
    wire sys_clk = pll_clk1;
    reg [1:0] sys_rst_sync;

    // The reset presented to all AXI peripherals (including BRAM) is fully
    // synchronous to the PLL clock.  It is released after two clock edges
    // once the external reset is inactive and the PLL is locked.
    always @(posedge pll_clk1) begin
        if (fpga_rst || !pll_lock)
            sys_rst_sync <= 2'b11;
        else
            sys_rst_sync <= {sys_rst_sync[0], 1'b0};
    end
    wire sys_rst = sys_rst_sync[1];

    clk_wiz_0 U_clkgen (
        .clk_in1(fpga_clk),
        .locked(pll_lock),
        .clk_out1(pll_clk1)
    );
`endif

    wire [31:0] cpu_inst;
    wire [31:0] cpu_pc;

    // Shared AXI4 link between the cache hierarchy and the SoC bus.
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

    wire [31:0] sw_awaddr, led_awaddr, digled_awaddr, uart_awaddr, tim_awaddr;
    wire [7:0]  sw_awlen, led_awlen, digled_awlen, uart_awlen, tim_awlen;
    wire [2:0]  sw_awsize, led_awsize, digled_awsize, uart_awsize, tim_awsize;
    wire [1:0]  sw_awburst, led_awburst, digled_awburst, uart_awburst, tim_awburst;
    wire        sw_awvalid, led_awvalid, digled_awvalid, uart_awvalid, tim_awvalid;
    wire        sw_awready, led_awready, digled_awready, uart_awready, tim_awready;
    wire [31:0] sw_wdata, led_wdata, digled_wdata, uart_wdata, tim_wdata;
    wire [3:0]  sw_wstrb, led_wstrb, digled_wstrb, uart_wstrb, tim_wstrb;
    wire        sw_wlast, led_wlast, digled_wlast, uart_wlast, tim_wlast;
    wire        sw_wvalid, led_wvalid, digled_wvalid, uart_wvalid, tim_wvalid;
    wire        sw_wready, led_wready, digled_wready, uart_wready, tim_wready;
    wire [1:0]  sw_bresp, led_bresp, digled_bresp, uart_bresp, tim_bresp;
    wire        sw_bvalid, led_bvalid, digled_bvalid, uart_bvalid, tim_bvalid;
    wire        sw_bready, led_bready, digled_bready, uart_bready, tim_bready;
    wire [31:0] sw_araddr, led_araddr, digled_araddr, uart_araddr, tim_araddr;
    wire [7:0]  sw_arlen, led_arlen, digled_arlen, uart_arlen, tim_arlen;
    wire [2:0]  sw_arsize, led_arsize, digled_arsize, uart_arsize, tim_arsize;
    wire [1:0]  sw_arburst, led_arburst, digled_arburst, uart_arburst, tim_arburst;
    wire        sw_arvalid, led_arvalid, digled_arvalid, uart_arvalid, tim_arvalid;
    wire        sw_arready, led_arready, digled_arready, uart_arready, tim_arready;
    wire [31:0] sw_rdata, led_rdata, digled_rdata, uart_rdata, tim_rdata;
    wire [1:0]  sw_rresp, led_rresp, digled_rresp, uart_rresp, tim_rresp;
    wire        sw_rlast, led_rlast, digled_rlast, uart_rlast, tim_rlast;
    wire        sw_rvalid, led_rvalid, digled_rvalid, uart_rvalid, tim_rvalid;
    wire        sw_rready, led_rready, digled_rready, uart_rready, tim_rready;

`ifdef RUN_TRACE
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
`else
    assign axi_bid = 4'b0;
    assign axi_rid = 4'b0;

    axi_crossbar_0 U_bridge (
        .aclk               (sys_clk),
        .aresetn            (!sys_rst),
        .s_axi_awaddr       (axi_awaddr),
        .s_axi_awlen        (axi_awlen),
        .s_axi_awsize       (axi_awsize),
        .s_axi_awburst      (axi_awburst),
        .s_axi_awlock       (1'b0),
        .s_axi_awcache      (4'h0),
        .s_axi_awprot       (3'h0),
        .s_axi_awqos        (4'h0),
        .s_axi_awvalid      (axi_awvalid),
        .s_axi_awready      (axi_awready),
        .s_axi_wdata        (axi_wdata),
        .s_axi_wstrb        (axi_wstrb),
        .s_axi_wlast        (axi_wlast),
        .s_axi_wvalid       (axi_wvalid),
        .s_axi_wready       (axi_wready),
        .s_axi_bresp        (axi_bresp),
        .s_axi_bvalid       (axi_bvalid),
        .s_axi_bready       (axi_bready),
        .s_axi_araddr       (axi_araddr),
        .s_axi_arlen        (axi_arlen),
        .s_axi_arsize       (axi_arsize),
        .s_axi_arburst      (axi_arburst),
        .s_axi_arlock       (1'b0),
        .s_axi_arcache      (4'h0),
        .s_axi_arprot       (3'h0),
        .s_axi_arqos        (4'h0),
        .s_axi_arvalid      (axi_arvalid),
        .s_axi_arready      (axi_arready),
        .s_axi_rdata        (axi_rdata),
        .s_axi_rresp        (axi_rresp),
        .s_axi_rlast        (axi_rlast),
        .s_axi_rvalid       (axi_rvalid),
        .s_axi_rready       (axi_rready),
        .m_axi_awaddr       ({tim_awaddr, uart_awaddr, digled_awaddr, led_awaddr, sw_awaddr, bram_awaddr}),
        .m_axi_awlen        ({tim_awlen, uart_awlen, digled_awlen, led_awlen, sw_awlen, bram_awlen}),
        .m_axi_awsize       ({tim_awsize, uart_awsize, digled_awsize, led_awsize, sw_awsize, bram_awsize}),
        .m_axi_awburst      ({tim_awburst, uart_awburst, digled_awburst, led_awburst, sw_awburst, bram_awburst}),
        .m_axi_awvalid      ({tim_awvalid, uart_awvalid, digled_awvalid, led_awvalid, sw_awvalid, bram_awvalid}),
        .m_axi_awready      ({tim_awready, uart_awready, digled_awready, led_awready, sw_awready, bram_awready}),
        .m_axi_wdata        ({tim_wdata, uart_wdata, digled_wdata, led_wdata, sw_wdata, bram_wdata}),
        .m_axi_wstrb        ({tim_wstrb, uart_wstrb, digled_wstrb, led_wstrb, sw_wstrb, bram_wstrb}),
        .m_axi_wlast        ({tim_wlast, uart_wlast, digled_wlast, led_wlast, sw_wlast, bram_wlast}),
        .m_axi_wvalid       ({tim_wvalid, uart_wvalid, digled_wvalid, led_wvalid, sw_wvalid, bram_wvalid}),
        .m_axi_wready       ({tim_wready, uart_wready, digled_wready, led_wready, sw_wready, bram_wready}),
        .m_axi_bresp        ({tim_bresp, uart_bresp, digled_bresp, led_bresp, sw_bresp, bram_bresp}),
        .m_axi_bvalid       ({tim_bvalid, uart_bvalid, digled_bvalid, led_bvalid, sw_bvalid, bram_bvalid}),
        .m_axi_bready       ({tim_bready, uart_bready, digled_bready, led_bready, sw_bready, bram_bready}),
        .m_axi_araddr       ({tim_araddr, uart_araddr, digled_araddr, led_araddr, sw_araddr, bram_araddr}),
        .m_axi_arlen        ({tim_arlen, uart_arlen, digled_arlen, led_arlen, sw_arlen, bram_arlen}),
        .m_axi_arsize       ({tim_arsize, uart_arsize, digled_arsize, led_arsize, sw_arsize, bram_arsize}),
        .m_axi_arburst      ({tim_arburst, uart_arburst, digled_arburst, led_arburst, sw_arburst, bram_arburst}),
        .m_axi_arvalid      ({tim_arvalid, uart_arvalid, digled_arvalid, led_arvalid, sw_arvalid, bram_arvalid}),
        .m_axi_arready      ({tim_arready, uart_arready, digled_arready, led_arready, sw_arready, bram_arready}),
        .m_axi_rdata        ({tim_rdata, uart_rdata, digled_rdata, led_rdata, sw_rdata, bram_rdata}),
        .m_axi_rresp        ({tim_rresp, uart_rresp, digled_rresp, led_rresp, sw_rresp, bram_rresp}),
        .m_axi_rlast        ({tim_rlast, uart_rlast, digled_rlast, led_rlast, sw_rlast, bram_rlast}),
        .m_axi_rvalid       ({tim_rvalid, uart_rvalid, digled_rvalid, led_rvalid, sw_rvalid, bram_rvalid}),
        .m_axi_rready       ({tim_rready, uart_rready, digled_rready, led_rready, sw_rready, bram_rready})
    );

    switch_wrap U_switch (
        .aclk           (sys_clk),
        .aresetn        (!sys_rst),
        .s_axi_awaddr   (sw_awaddr),
        .s_axi_awlen    (sw_awlen),
        .s_axi_awsize   (sw_awsize),
        .s_axi_awburst  (sw_awburst),
        .s_axi_awlock   (1'b0),
        .s_axi_awcache  (4'h0),
        .s_axi_awprot   (3'h0),
        .s_axi_awregion (4'h0),
        .s_axi_awqos    (4'h0),
        .s_axi_awvalid  (sw_awvalid),
        .s_axi_awready  (sw_awready),
        .s_axi_wdata    (sw_wdata),
        .s_axi_wstrb    (sw_wstrb),
        .s_axi_wlast    (sw_wlast),
        .s_axi_wvalid   (sw_wvalid),
        .s_axi_wready   (sw_wready),
        .s_axi_bresp    (sw_bresp),
        .s_axi_bvalid   (sw_bvalid),
        .s_axi_bready   (sw_bready),
        .s_axi_araddr   (sw_araddr),
        .s_axi_arlen    (sw_arlen),
        .s_axi_arsize   (sw_arsize),
        .s_axi_arburst  (sw_arburst),
        .s_axi_arlock   (1'b0),
        .s_axi_arcache  (4'h0),
        .s_axi_arprot   (3'h0),
        .s_axi_arregion (4'h0),
        .s_axi_arqos    (4'h0),
        .s_axi_arvalid  (sw_arvalid),
        .s_axi_arready  (sw_arready),
        .s_axi_rdata    (sw_rdata),
        .s_axi_rresp    (sw_rresp),
        .s_axi_rlast    (sw_rlast),
        .s_axi_rvalid   (sw_rvalid),
        .s_axi_rready   (sw_rready),
        .switch         (sw)
    );

    led_wrap U_led (
        .aclk           (sys_clk),
        .aresetn        (!sys_rst),
        .s_axi_awaddr   (led_awaddr),
        .s_axi_awlen    (led_awlen),
        .s_axi_awsize   (led_awsize),
        .s_axi_awburst  (led_awburst),
        .s_axi_awlock   (1'b0),
        .s_axi_awcache  (4'h0),
        .s_axi_awprot   (3'h0),
        .s_axi_awregion (4'h0),
        .s_axi_awqos    (4'h0),
        .s_axi_awvalid  (led_awvalid),
        .s_axi_awready  (led_awready),
        .s_axi_wdata    (led_wdata),
        .s_axi_wstrb    (led_wstrb),
        .s_axi_wlast    (led_wlast),
        .s_axi_wvalid   (led_wvalid),
        .s_axi_wready   (led_wready),
        .s_axi_bresp    (led_bresp),
        .s_axi_bvalid   (led_bvalid),
        .s_axi_bready   (led_bready),
        .s_axi_araddr   (led_araddr),
        .s_axi_arlen    (led_arlen),
        .s_axi_arsize   (led_arsize),
        .s_axi_arburst  (led_arburst),
        .s_axi_arlock   (1'b0),
        .s_axi_arcache  (4'h0),
        .s_axi_arprot   (3'h0),
        .s_axi_arregion (4'h0),
        .s_axi_arqos    (4'h0),
        .s_axi_arvalid  (led_arvalid),
        .s_axi_arready  (led_arready),
        .s_axi_rdata    (led_rdata),
        .s_axi_rresp    (led_rresp),
        .s_axi_rlast    (led_rlast),
        .s_axi_rvalid   (led_rvalid),
        .s_axi_rready   (led_rready),
        .led_o          (led)
    );

    digled_wrap U_digled (
        .aclk           (sys_clk),
        .aresetn        (!sys_rst),
        .s_axi_awaddr   (digled_awaddr),
        .s_axi_awlen    (digled_awlen),
        .s_axi_awsize   (digled_awsize),
        .s_axi_awburst  (digled_awburst),
        .s_axi_awlock   (1'b0),
        .s_axi_awcache  (4'h0),
        .s_axi_awprot   (3'h0),
        .s_axi_awregion (4'h0),
        .s_axi_awqos    (4'h0),
        .s_axi_awvalid  (digled_awvalid),
        .s_axi_awready  (digled_awready),
        .s_axi_wdata    (digled_wdata),
        .s_axi_wstrb    (digled_wstrb),
        .s_axi_wlast    (digled_wlast),
        .s_axi_wvalid   (digled_wvalid),
        .s_axi_wready   (digled_wready),
        .s_axi_bresp    (digled_bresp),
        .s_axi_bvalid   (digled_bvalid),
        .s_axi_bready   (digled_bready),
        .s_axi_araddr   (digled_araddr),
        .s_axi_arlen    (digled_arlen),
        .s_axi_arsize   (digled_arsize),
        .s_axi_arburst  (digled_arburst),
        .s_axi_arlock   (1'b0),
        .s_axi_arcache  (4'h0),
        .s_axi_arprot   (3'h0),
        .s_axi_arregion (4'h0),
        .s_axi_arqos    (4'h0),
        .s_axi_arvalid  (digled_arvalid),
        .s_axi_arready  (digled_arready),
        .s_axi_rdata    (digled_rdata),
        .s_axi_rresp    (digled_rresp),
        .s_axi_rlast    (digled_rlast),
        .s_axi_rvalid   (digled_rvalid),
        .s_axi_rready   (digled_rready),
        .dig_en         (dig_en),
        .dig_seg        (dig_seg)
    );
`endif

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

    cpu_top U_cpu (
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

`ifndef RUN_TRACE
    uart_wrap U_uart (
        .aclk           (sys_clk),
        .aresetn        (!sys_rst),
        .s_axi_awaddr   (uart_awaddr),
        .s_axi_awlen    (uart_awlen),
        .s_axi_awsize   (uart_awsize),
        .s_axi_awburst  (uart_awburst),
        .s_axi_awlock   (1'b0),
        .s_axi_awcache  (4'h0),
        .s_axi_awprot   (3'h0),
        .s_axi_awregion (4'h0),
        .s_axi_awqos    (4'h0),
        .s_axi_awvalid  (uart_awvalid),
        .s_axi_awready  (uart_awready),
        .s_axi_wdata    (uart_wdata),
        .s_axi_wstrb    (uart_wstrb),
        .s_axi_wlast    (uart_wlast),
        .s_axi_wvalid   (uart_wvalid),
        .s_axi_wready   (uart_wready),
        .s_axi_bresp    (uart_bresp),
        .s_axi_bvalid   (uart_bvalid),
        .s_axi_bready   (uart_bready),
        .s_axi_araddr   (uart_araddr),
        .s_axi_arlen    (uart_arlen),
        .s_axi_arsize   (uart_arsize),
        .s_axi_arburst  (uart_arburst),
        .s_axi_arlock   (1'b0),
        .s_axi_arcache  (4'h0),
        .s_axi_arprot   (3'h0),
        .s_axi_arregion (4'h0),
        .s_axi_arqos    (4'h0),
        .s_axi_arvalid  (uart_arvalid),
        .s_axi_arready  (uart_arready),
        .s_axi_rdata    (uart_rdata),
        .s_axi_rresp    (uart_rresp),
        .s_axi_rlast    (uart_rlast),
        .s_axi_rvalid   (uart_rvalid),
        .s_axi_rready   (uart_rready),
        .rx             (rx),
        .tx             (tx)
    );

    timer_wrap U_timer (
        .aclk           (sys_clk),
        .aresetn        (!sys_rst),
        .s_axi_awaddr   (tim_awaddr),
        .s_axi_awlen    (tim_awlen),
        .s_axi_awsize   (tim_awsize),
        .s_axi_awburst  (tim_awburst),
        .s_axi_awlock   (1'b0),
        .s_axi_awcache  (4'h0),
        .s_axi_awprot   (3'h0),
        .s_axi_awregion (4'h0),
        .s_axi_awqos    (4'h0),
        .s_axi_awvalid  (tim_awvalid),
        .s_axi_awready  (tim_awready),
        .s_axi_wdata    (tim_wdata),
        .s_axi_wstrb    (tim_wstrb),
        .s_axi_wlast    (tim_wlast),
        .s_axi_wvalid   (tim_wvalid),
        .s_axi_wready   (tim_wready),
        .s_axi_bresp    (tim_bresp),
        .s_axi_bvalid   (tim_bvalid),
        .s_axi_bready   (tim_bready),
        .s_axi_araddr   (tim_araddr),
        .s_axi_arlen    (tim_arlen),
        .s_axi_arsize   (tim_arsize),
        .s_axi_arburst  (tim_arburst),
        .s_axi_arlock   (1'b0),
        .s_axi_arcache  (4'h0),
        .s_axi_arprot   (3'h0),
        .s_axi_arregion (4'h0),
        .s_axi_arqos    (4'h0),
        .s_axi_arvalid  (tim_arvalid),
        .s_axi_arready  (tim_arready),
        .s_axi_rdata    (tim_rdata),
        .s_axi_rresp    (tim_rresp),
        .s_axi_rlast    (tim_rlast),
        .s_axi_rvalid   (tim_rvalid),
        .s_axi_rready   (tim_rready)
    );
`else
    assign tx = 1'b1;
    assign led = 24'b0;
    assign dig_en = 8'b0;
    assign dig_seg = 8'b0;
`endif
endmodule

/*
module digled_wrap(
    input  wire         aclk,
    input  wire         aresetn,
    input  wire [31:0]  s_axi_awaddr,
    input  wire [ 7:0]  s_axi_awlen,
    input  wire [ 2:0]  s_axi_awsize,
    input  wire [ 1:0]  s_axi_awburst,
    input  wire [ 0:0]  s_axi_awlock,
    input  wire [ 3:0]  s_axi_awcache,
    input  wire [ 2:0]  s_axi_awprot,
    input  wire [ 3:0]  s_axi_awregion,
    input  wire [ 3:0]  s_axi_awqos,
    input  wire         s_axi_awvalid,
    output wire         s_axi_awready,
    input  wire [31:0]  s_axi_wdata,
    input  wire [ 3:0]  s_axi_wstrb,
    input  wire         s_axi_wlast,
    input  wire         s_axi_wvalid,
    output wire         s_axi_wready,
    output wire [ 1:0]  s_axi_bresp,
    output wire         s_axi_bvalid,
    input  wire         s_axi_bready,
    input  wire [31:0]  s_axi_araddr,
    input  wire [ 7:0]  s_axi_arlen,
    input  wire [ 2:0]  s_axi_arsize,
    input  wire [ 1:0]  s_axi_arburst,
    input  wire [ 0:0]  s_axi_arlock,
    input  wire [ 3:0]  s_axi_arcache,
    input  wire [ 2:0]  s_axi_arprot,
    input  wire [ 3:0]  s_axi_arregion,
    input  wire [ 3:0]  s_axi_arqos,
    input  wire         s_axi_arvalid,
    output wire         s_axi_arready,
    output wire [31:0]  s_axi_rdata,
    output wire [ 1:0]  s_axi_rresp,
    output wire         s_axi_rlast,
    output wire         s_axi_rvalid,
    input  wire         s_axi_rready,
    output wire [ 7:0]  dig_en,
    output wire [ 7:0]  dig_seg
);
    wire [31:0] digled_data;

    assign dig_en  = digled_data[7:0];
    assign dig_seg = digled_data[15:8];

    axi4_mmio_reg #(
        .WRITABLE(1'b1),
        .RESET_VALUE(32'b0)
    ) U_mmio (
        .aclk(aclk),
        .aresetn(aresetn),
        .s_axi_awvalid(s_axi_awvalid),
        .s_axi_awready(s_axi_awready),
        .s_axi_wdata(s_axi_wdata),
        .s_axi_wstrb(s_axi_wstrb),
        .s_axi_wvalid(s_axi_wvalid),
        .s_axi_wready(s_axi_wready),
        .s_axi_bresp(s_axi_bresp),
        .s_axi_bvalid(s_axi_bvalid),
        .s_axi_bready(s_axi_bready),
        .s_axi_arvalid(s_axi_arvalid),
        .s_axi_arready(s_axi_arready),
        .s_axi_rdata(s_axi_rdata),
        .s_axi_rresp(s_axi_rresp),
        .s_axi_rlast(s_axi_rlast),
        .s_axi_rvalid(s_axi_rvalid),
        .s_axi_rready(s_axi_rready),
        .read_data({16'b0, dig_seg, dig_en}),
        .write_data(digled_data)
    );
endmodule

module axi4_mmio_reg #(
    parameter WRITABLE = 1'b1,
    parameter [31:0] RESET_VALUE = 32'b0
) (
    input  wire        aclk,
    input  wire        aresetn,
    input  wire        s_axi_awvalid,
    output wire        s_axi_awready,
    input  wire [31:0] s_axi_wdata,
    input  wire [3:0]  s_axi_wstrb,
    input  wire        s_axi_wvalid,
    output wire        s_axi_wready,
    output reg  [1:0]  s_axi_bresp,
    output reg         s_axi_bvalid,
    input  wire        s_axi_bready,
    input  wire        s_axi_arvalid,
    output wire        s_axi_arready,
    output reg  [31:0] s_axi_rdata,
    output reg  [1:0]  s_axi_rresp,
    output reg         s_axi_rlast,
    output reg         s_axi_rvalid,
    input  wire        s_axi_rready,
    input  wire [31:0] read_data,
    output reg  [31:0] write_data
);
    reg aw_seen;
    reg w_seen;
    reg [31:0] wdata_reg;
    reg [3:0]  wstrb_reg;

    assign s_axi_awready = !aw_seen && !s_axi_bvalid;
    assign s_axi_wready  = !w_seen && !s_axi_bvalid;
    assign s_axi_arready = !s_axi_rvalid;

    wire aw_fire = s_axi_awvalid && s_axi_awready;
    wire w_fire  = s_axi_wvalid && s_axi_wready;
    wire ar_fire = s_axi_arvalid && s_axi_arready;

    function [31:0] apply_wstrb;
        input [31:0] old_data;
        input [31:0] new_data;
        input [3:0]  strb;
        integer i;
        begin
            apply_wstrb = old_data;
            for (i = 0; i < 4; i = i + 1) begin
                if (strb[i]) apply_wstrb[i * 8 +: 8] = new_data[i * 8 +: 8];
            end
        end
    endfunction

    wire [31:0] active_wdata = w_fire ? s_axi_wdata : wdata_reg;
    wire [3:0]  active_wstrb = w_fire ? s_axi_wstrb : wstrb_reg;

    always @(posedge aclk or negedge aresetn) begin
        if (!aresetn) begin
            aw_seen      <= 1'b0;
            w_seen       <= 1'b0;
            wdata_reg    <= 32'b0;
            wstrb_reg    <= 4'b0;
            s_axi_bresp  <= 2'b00;
            s_axi_bvalid <= 1'b0;
            write_data   <= RESET_VALUE;
        end else begin
            if (s_axi_bvalid) begin
                if (s_axi_bready) begin
                    s_axi_bvalid <= 1'b0;
                end
            end else begin
                if (aw_fire) aw_seen <= 1'b1;
                if (w_fire) begin
                    w_seen    <= 1'b1;
                    wdata_reg <= s_axi_wdata;
                    wstrb_reg <= s_axi_wstrb;
                end

                if ((aw_seen || aw_fire) && (w_seen || w_fire)) begin
                    aw_seen      <= 1'b0;
                    w_seen       <= 1'b0;
                    if (WRITABLE) write_data <= apply_wstrb(write_data, active_wdata, active_wstrb);
                    s_axi_bresp  <= 2'b00;
                    s_axi_bvalid <= 1'b1;
                end
            end
        end
    end

    always @(posedge aclk or negedge aresetn) begin
        if (!aresetn) begin
            s_axi_rdata  <= 32'b0;
            s_axi_rresp  <= 2'b00;
            s_axi_rlast  <= 1'b0;
            s_axi_rvalid <= 1'b0;
        end else begin
            if (s_axi_rvalid) begin
                if (s_axi_rready) begin
                    s_axi_rlast  <= 1'b0;
                    s_axi_rvalid <= 1'b0;
                end
            end else if (ar_fire) begin
                s_axi_rdata  <= read_data;
                s_axi_rresp  <= 2'b00;
                s_axi_rlast  <= 1'b1;
                s_axi_rvalid <= 1'b1;
            end
        end
    end
endmodule
*/
