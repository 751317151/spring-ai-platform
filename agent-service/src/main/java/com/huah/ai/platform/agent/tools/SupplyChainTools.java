package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 供应链领域工具集
 */
@Slf4j
@Component
public class SupplyChainTools {

    @Tool(description = "查询库存状态，获取指定物料或产品的当前库存数量")
    public Map<String, Object> queryInventory(
            @ToolParam(description = "物料编号或产品编号") String materialId,
            @ToolParam(description = "仓库编号，可为空查询所有仓库") String warehouseId) {
        log.info("[Tool] queryInventory: material={}, warehouse={}", materialId, warehouseId);
        return Map.of(
                "materialId", materialId,
                "materialName", "高精度传感器芯片",
                "totalStock", 5000,
                "availableStock", 3200,
                "reservedStock", 1800,
                "unit", "个",
                "safetyStock", 1000,
                "reorderPoint", 1500,
                "status", "SUFFICIENT"
        );
    }

    @Tool(description = "查询采购订单状态")
    public Map<String, Object> queryPurchaseOrder(
            @ToolParam(description = "采购订单号，如 PO-2024-001，或供应商名称") String orderOrSupplier) {
        log.info("[Tool] queryPO: {}", orderOrSupplier);
        return Map.of(
                "orderId", "PO-2024-001",
                "supplier", "ABC电子器件有限公司",
                "status", "IN_TRANSIT",
                "expectedDelivery", LocalDate.now().plusDays(7).toString(),
                "items", List.of(
                        Map.of("material", "传感器芯片", "quantity", 2000, "unitPrice", 45.5)
                ),
                "totalAmount", 91000
        );
    }
}
