`timescale 1ns / 1ps

module cpu_top(
    input  wire         cpu_clk,
    input  wire         cpu_rst
);

    wire        cpu2ic_rreq;
    wire [31:0] cpu2ic_addr;
    reg         ic2cpu_valid;
    wire [31:0] ic2cpu_inst;
    wire [3:0]  cpu2dc_ren;
    wire [31:0] cpu2dc_addr;
    reg         dc2cpu_valid;
    wire [31:0] dc2cpu_rdata;
    wire [3:0]  cpu2dc_wen;
    wire [31:0] cpu2dc_wdata;
    reg         dc2cpu_wresp;

    always @(posedge cpu_clk) begin
        if (cpu_rst) begin
            ic2cpu_valid <= 1'b0;
            dc2cpu_valid <= 1'b0;
            dc2cpu_wresp <= 1'b0;
        end else begin
            ic2cpu_valid <= cpu2ic_rreq;
            dc2cpu_valid <= |cpu2dc_ren;
            dc2cpu_wresp <= |cpu2dc_wen;
        end
    end

    IROM U_irom (
        .clka  (cpu_clk),
        .addra (cpu2ic_addr[15:2]),
        .douta (ic2cpu_inst)
    );

    DRAM U_dram (
        .clka  (cpu_clk),
        .wea   (cpu2dc_wen),
        .addra (cpu2dc_addr[16:2]),
        .dina  (cpu2dc_wdata),
        .douta (dc2cpu_rdata)
    );

    cpu_core U_core (
        .cpu_clk        (cpu_clk),
        .cpu_rst        (cpu_rst),
        .ifetch_req     (cpu2ic_rreq),
        .ifetch_addr    (cpu2ic_addr),
        .ifetch_valid   (ic2cpu_valid),
        .ifetch_inst    (ic2cpu_inst),
        .daccess_ren    (cpu2dc_ren),
        .daccess_addr   (cpu2dc_addr),
        .daccess_rvalid (dc2cpu_valid),
        .daccess_rdata  (dc2cpu_rdata),
        .daccess_wen    (cpu2dc_wen),
        .daccess_wdata  (cpu2dc_wdata),
        .daccess_wresp  (dc2cpu_wresp)
    );

endmodule
