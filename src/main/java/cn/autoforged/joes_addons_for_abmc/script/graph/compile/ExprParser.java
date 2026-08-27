package cn.autoforged.joes_addons_for_abmc.script.graph.compile;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.expr.BinaryArithExpr;
import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;
import cn.autoforged.joes_addons_for_abmc.script.expr.LiteralExpr;
import cn.autoforged.joes_addons_for_abmc.script.expr.UnaryArithExpr;
import cn.autoforged.joes_addons_for_abmc.script.expr.VariableExpr;

/**
 * 内联表达式解析器：把图模型中“简单值”的文本表达式解析为运行时 {@link Expr}。
 * <p>
 * 支持的语法：
 * <ul>
 *   <li>数字：{@code 123}、{@code 3.14}、{@code -5}</li>
 *   <li>字符串：{@code "..."} 或 {@code '...'}</li>
 *   <li>布尔：{@code true}/{@code false}；空：{@code null}</li>
 *   <li>变量引用：{@code $变量名}</li>
 *   <li>算术：{@code + - * / %}，一元负号，括号</li>
 * </ul>
 * 解析失败时返回 null 值字面量，避免编译期崩溃。
 */
public final class ExprParser {
    private final String text;
    private int pos;

    private ExprParser(String text) {
        this.text = text;
        this.pos = 0;
    }

    public static Expr parse(String text) {
        if (text == null) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
        try {
            ExprParser p = new ExprParser(text);
            Expr e = p.parseAddSub();
            p.skipWhitespace();
            if (p.pos < p.text.length()) {
                throw new IllegalArgumentException("unexpected trailing token");
            }
            return e;
        } catch (Exception ex) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
    }

    private Expr parseAddSub() {
        Expr left = parseMulDiv();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                left = new BinaryArithExpr(left, BinaryArithExpr.Op.ADD, parseMulDiv());
            } else if (match('-')) {
                left = new BinaryArithExpr(left, BinaryArithExpr.Op.SUB, parseMulDiv());
            } else {
                return left;
            }
        }
    }

    private Expr parseMulDiv() {
        Expr left = parseUnary();
        while (true) {
            skipWhitespace();
            if (match('*')) {
                left = new BinaryArithExpr(left, BinaryArithExpr.Op.MUL, parseUnary());
            } else if (match('/')) {
                left = new BinaryArithExpr(left, BinaryArithExpr.Op.DIV, parseUnary());
            } else if (match('%')) {
                left = new BinaryArithExpr(left, BinaryArithExpr.Op.MOD, parseUnary());
            } else {
                return left;
            }
        }
    }

    private Expr parseUnary() {
        skipWhitespace();
        if (match('-')) {
            return new UnaryArithExpr(parseUnary());
        }
        if (match('+')) {
            return parseUnary();
        }
        return parsePrimary();
    }

    private Expr parsePrimary() {
        skipWhitespace();
        if (pos >= text.length()) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
        char c = text.charAt(pos);

        // 字符串字面量
        if (c == '"' || c == '\'') {
            return new LiteralExpr(ScriptValue.ofString(parseString(c)));
        }

        // 变量引用：$名称
        if (c == '$') {
            pos++;
            int start = pos;
            while (pos < text.length() && isVarChar(text.charAt(pos))) {
                pos++;
            }
            String name = text.substring(start, pos);
            return new VariableExpr(name);
        }

        // 括号
        if (c == '(') {
            pos++;
            Expr inner = parseAddSub();
            skipWhitespace();
            expect(')');
            return inner;
        }

        // 数字 / 关键字
        if (isDigit(c) || c == '.') {
            return parseNumber();
        }

        // 关键字 true/false/null
        String word = readWord();
        switch (word) {
            case "true":
                return new LiteralExpr(ScriptValue.ofNumber(1));
            case "false":
                return new LiteralExpr(ScriptValue.ofNumber(0));
            case "null":
                return new LiteralExpr(ScriptValue.nullValue());
            default:
                return new LiteralExpr(ScriptValue.nullValue());
        }
    }

    private Expr parseNumber() {
        int start = pos;
        while (pos < text.length() && (isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
            pos++;
        }
        String token = text.substring(start, pos);
        try {
            return new LiteralExpr(ScriptValue.ofNumber(Double.parseDouble(token)));
        } catch (NumberFormatException e) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
    }

    private String parseString(char quote) {
        pos++; // 跳过起始引号
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\\' && pos + 1 < text.length()) {
                char n = text.charAt(pos + 1);
                if (n == '\\' || n == quote) {
                    sb.append(n);
                    pos += 2;
                    continue;
                }
                sb.append(c);
                pos++;
                continue;
            }
            if (c == quote) {
                pos++;
                return sb.toString();
            }
            sb.append(c);
            pos++;
        }
        return sb.toString();
    }

    private String readWord() {
        int start = pos;
        while (pos < text.length() && isWordChar(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private boolean match(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isVarChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isWordChar(char c) {
        return Character.isLetter(c);
    }
}