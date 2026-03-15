package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * HR/行政领域工具集
 */
@Slf4j
@Component
public class HrTools {

    @Tool(description = "查询员工信息，包括部门、职级、在职状态")
    public Map<String, Object> queryEmployee(
            @ToolParam(description = "员工工号或姓名") String employeeIdOrName) {
        log.info("[Tool] queryEmployee: {}", employeeIdOrName);
        return Map.of(
                "employeeId", "EMP001",
                "name", "张三",
                "department", "研发中心",
                "position", "高级工程师",
                "level", "P6",
                "status", "ACTIVE",
                "manager", "李四"
        );
    }

    @Tool(description = "提交审批申请，如请假、报销、采购等，返回审批单号")
    public Map<String, String> submitApproval(
            @ToolParam(description = "审批类型: LEAVE|EXPENSE|PURCHASE|OTHER") String type,
            @ToolParam(description = "申请内容 JSON 字符串") String content,
            @ToolParam(description = "申请人工号") String applicantId) {
        log.info("[Tool] submitApproval: type={}, applicant={}", type, applicantId);
        String approvalNo = "APV-" + System.currentTimeMillis();
        return Map.of(
                "approvalNo", approvalNo,
                "status", "SUBMITTED",
                "message", "审批申请已提交，审批单号: " + approvalNo
        );
    }
}
