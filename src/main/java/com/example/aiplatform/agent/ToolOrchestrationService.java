package com.example.aiplatform.agent;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ToolOrchestrationService {

    public String invokeBusinessTools(String task, Map<String, Object> context) {
        if (task.contains("审批") || task.contains("approve")) {
            return "已调用审批流 API 并返回审批状态。";
        }
        if (task.contains("库存") || task.contains("供应链")) {
            return "已调用供应链系统 API，获取库存与在途物流信息。";
        }
        if (task.contains("报价") || task.contains("价格")) {
            return "已调用 CRM/ERP 接口并生成报价建议。";
        }
        return "已调用通用企业工具集，返回标准化结果。";
    }
}
