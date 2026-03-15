package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 销售报价领域工具集
 */
@Slf4j
@Component
public class SalesTools {

    @Tool(description = "查询产品报价，获取指定产品的当前报价和历史价格趋势")
    public Map<String, Object> queryProductPrice(
            @ToolParam(description = "产品编号或产品名称") String productId,
            @ToolParam(description = "客户级别: A|B|C，影响折扣，可为空默认标准价") String customerLevel) {
        log.info("[Tool] queryPrice: product={}, customer={}", productId, customerLevel);
        double basePrice = 1999.0;
        double discount = "A".equals(customerLevel) ? 0.85 : "B".equals(customerLevel) ? 0.9 : 0.95;
        return Map.of(
                "productId", productId,
                "productName", "智能传感器 Model X",
                "listPrice", basePrice,
                "customerLevel", customerLevel != null ? customerLevel : "C",
                "discount", discount,
                "finalPrice", basePrice * discount,
                "currency", "CNY",
                "validUntil", LocalDate.now().plusDays(30).toString()
        );
    }
}
