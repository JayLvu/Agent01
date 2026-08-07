package com.vanzy.agent.tool.impl;

import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数学计算工具(内置 Skill 示例)
 *
 * 支持四则运算与括号,基于递归下降解析,无外部依赖、无代码注入风险。
 *
 * @author VanzyLiu
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "calculate";
    }

    @Override
    public String getDescription() {
        return "数学计算器,支持加减乘除、括号与小数。例如 (3 + 4) * 2 / 5";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "string");
        expr.put("description", "数学表达式,如 1+2*3 或 (1+2)/3");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("expression", expr);
        schema.put("properties", props);
        schema.put("required", java.util.List.of("expression"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        Object exprObj = arguments.get("expression");
        if (exprObj == null || String.valueOf(exprObj).isBlank()) {
            return ToolResult.error("expression 参数不能为空");
        }
        String expr = String.valueOf(exprObj).trim();
        try {
            BigDecimal result = new Parser(expr).parse();
            // 保留 8 位小数,去除末尾 0
            String out = result.setScale(8, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            return ToolResult.success(expr + " = " + out);
        } catch (Exception e) {
            return ToolResult.error("表达式解析失败: " + e.getMessage());
        }
    }

    /** 简易递归下降解析器 */
    private static class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) {
            this.s = s;
        }

        BigDecimal parse() {
            BigDecimal v = expr();
            if (pos < s.length()) {
                throw new IllegalArgumentException("未预期的字符: " + s.charAt(pos));
            }
            return v;
        }

        private BigDecimal expr() {
            BigDecimal v = term();
            while (pos < s.length()) {
                skipSpace();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c == '+') { pos++; v = v.add(term()); }
                else if (c == '-') { pos++; v = v.subtract(term()); }
                else break;
            }
            return v;
        }

        private BigDecimal term() {
            BigDecimal v = factor();
            while (pos < s.length()) {
                skipSpace();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c == '*') { pos++; v = v.multiply(factor()); }
                else if (c == '/') {
                    pos++;
                    v = v.divide(factor(), MathContext.DECIMAL128);
                }
                else break;
            }
            return v;
        }

        private BigDecimal factor() {
            skipSpace();
            if (pos >= s.length()) throw new IllegalArgumentException("表达式不完整");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                BigDecimal v = expr();
                skipSpace();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                pos++;
                return v;
            }
            if (c == '-') { pos++; return factor().negate(); }
            if (c == '+') { pos++; return factor(); }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("缺少数字,位置 " + pos);
            return new BigDecimal(s.substring(start, pos));
        }

        private void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
