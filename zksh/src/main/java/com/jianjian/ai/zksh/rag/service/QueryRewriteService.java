package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.rag.config.RagRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Query Rewriting：将用户问题改写为更适合检索的 query。
 * 失败时返回 null，由上游决定是否回退原 query。
 */
@Service
public class QueryRewriteService {
    /**
     * SLF4J 日志记录器，用于记录查询改写过程中的调试信息和异常
     * LoggerFactory.getLogger() 会根据当前类名创建对应的 Logger 实例
     */
    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    /**
     * 正则表达式模式：用于从 LLM 返回的 JSON 字符串中提取 rewrittenQuery 字段的值
     * Pattern.DOTALL 标志使 . 能够匹配包括换行符在内的所有字符
     * 匹配示例："rewrittenQuery" : "改写后的查询文本"
     */
    private static final Pattern JSON_REWRITTEN_QUERY = Pattern.compile("\"rewrittenQuery\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    /**
     * AI 服务依赖：负责调用大语言模型执行实际的查询改写任务
     * 通过构造函数注入，实现依赖倒置原则
     */
    private final QueryRewriteAiService aiService;

    /**
     * RAG 改写配置属性：包含是否启用改写、超时时间、最大长度等配置项
     * 通过构造函数注入，便于外部配置管理
     */
    private final RagRewriteProperties properties;

    /**
     * 构造函数：通过依赖注入初始化服务所需的核心组件
     *
     * @param aiService AI 改写服务，用于调用 LLM 执行查询改写
     * @param properties RAG 改写配置，控制改写行为的各项参数
     */
    public QueryRewriteService(QueryRewriteAiService aiService, RagRewriteProperties properties) {
        this.aiService = aiService;
        this.properties = properties;
    }

    /**
     * 核心方法：将原始用户查询改写为更适合向量检索的形式
     *
     * 执行流程：
     * 1. 检查功能开关：如果配置中未启用改写功能，直接返回 null
     * 2. 参数校验：检查原始查询是否为空或空白字符串
     * 3. 标准化处理：去除多余空白字符和换行符
     * 4. 异步调用 LLM：使用 CompletableFuture 执行非阻塞的 AI 改写请求
     * 5. 超时控制：通过 orTimeout 限制最大等待时间，避免长时间阻塞
     * 6. 结果提取：从 LLM 返回的 JSON 中解析出 rewrittenQuery 字段
     * 7. 数据清洗：去除特殊字符、标点符号等噪声
     * 8. 长度限制：确保改写后的查询不超过配置的最大长度
     * 9. 有效性验证：如果改写结果与原查询相同或为空，返回 null（表示改写无效）
     * 10. 异常处理：任何异常都会捕获并返回 null，保证系统稳定性
     *
     * @param originalQuery 用户的原始查询文本
     * @return RewriteResult 包含原始查询、改写后查询和原始 JSON 响应；如果改写失败或无效则返回 null
     */
    public RewriteResult rewriteOrNull(String originalQuery) {
        // 步骤1：检查配置开关，如果未启用改写功能则直接返回 null
        // properties.getEnabled() 可能为 null，需要先判断再取值
        if (properties.getEnabled() == null || !properties.getEnabled()) {
            return null;
        }

        // 步骤2：校验原始查询是否为有效文本（非 null、非空字符串、非纯空白）
        // StringUtils.hasText() 会检查 null、空字符串和仅包含空白字符的情况
        if (!StringUtils.hasText(originalQuery)) {
            return null;
        }

        // 步骤3：标准化原始查询，去除多余的换行符和空白字符
        // normalize() 会将多个连续空白合并为一个空格，并去除首尾空白
        String q = normalize(originalQuery);

        // 步骤4：再次校验标准化后的查询，确保处理后仍有有效内容
        if (!StringUtils.hasText(q)) {
            return null;
        }

        // 步骤5：获取超时配置，设置默认值为 1200 毫秒，最小值为 200 毫秒
        // Math.max() 确保超时时间不会设置得过小，避免过早超时
        int timeoutMs = properties.getTimeoutMs() == null ? 1200 : properties.getTimeoutMs();
        timeoutMs = Math.max(200, timeoutMs);

        try {
            // 步骤6：异步执行 LLM 改写请求
            // CompletableFuture.supplyAsync() 在 ForkJoinPool 的线程池中异步执行 aiService.rewrite(q)
            // 这样不会阻塞主线程，提高系统并发性能
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> aiService.rewrite(q));

            // 步骤7：等待异步任务完成，但设置超时限制
            // orTimeout() 会在指定时间后自动取消任务并抛出 TimeoutException
            // join() 阻塞等待结果返回，如果超时或异常会抛出 CompletionException
            String raw = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();

            // 步骤8：从 LLM 返回的原始 JSON 字符串中提取 rewrittenQuery 字段
            // extractRewrittenQuery() 使用正则表达式解析 JSON，并进行必要的转义处理
            String rewritten = extractRewrittenQuery(raw);

            // 步骤9：清洗提取出的改写文本，去除噪声字符和格式问题
            // cleanup() 会处理换行符、多余空格、前缀标记和尾部标点等
            rewritten = cleanup(rewritten);

            // 步骤10：强制限制改写文本的最大长度，防止过长的查询影响检索效果
            // enforceMaxLength() 会截断超过配置的文本，默认最大长度为 120 字符
            rewritten = enforceMaxLength(rewritten, properties.getMaxLength() == null ? 120 : properties.getMaxLength());

            // 步骤11：验证改写结果的有效性
            // 如果清洗后为空，说明改写失败或内容为空，返回 null
            if (!StringUtils.hasText(rewritten)) {
                return null;
            }

            // 步骤12：检查改写是否有实际变化
            // 如果改写后的文本与标准化后的原文本相同，说明 LLM 没有做有效改写，返回 null
            if (rewritten.equals(q)) {
                return null;
            }

            // 步骤13：构造并返回成功的改写结果
            // RewriteResult record 封装了原始查询、改写后查询和原始 JSON 响应
            return new RewriteResult(q, rewritten, raw);

        } catch (Exception e) {
            // 步骤14：异常处理 - 任何异常都记录调试日志并返回 null
            // 使用 debug 级别避免生产环境日志过多
            // 返回 null 让上游调用者决定如何使用原始查询进行回退
            log.debug("Query rewriting 失败，回退原 query。", e);
            return null;
        }
    }

    /**
     * 从 LLM 返回的原始 JSON 字符串中提取 rewrittenQuery 字段的值
     *
     * 处理流程：
     * 1. 检查输入是否为有效文本
     * 2. 去除首尾空白字符
     * 3. 使用预编译的正则表达式匹配 "rewrittenQuery" : "值" 的模式
     * 4. 如果匹配成功，提取捕获组中的值并进行 JSON 转义反转
     * 5. 如果匹配失败，返回 null
     *
     * @param raw LLM 返回的原始 JSON 字符串
     * @return 提取出的改写查询文本，如果提取失败则返回 null
     */
    private String extractRewrittenQuery(String raw) {
        // 校验输入是否为有效文本，避免对 null 或空字符串进行处理
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        // 去除首尾空白字符，确保正则匹配的准确性
        String s = raw.trim();

        // 使用预编译的正则表达式模式匹配 JSON 中的 rewrittenQuery 字段
        // matcher() 创建匹配器对象，find() 查找第一个匹配的子序列
        Matcher m = JSON_REWRITTEN_QUERY.matcher(s);

        // 如果找到匹配项，提取第一个捕获组（即括号内的内容）
        // m.group(1) 返回正则表达式中第一个 () 捕获的内容
        if (m.find()) {
            // unescapeJsonString() 处理 JSON 字符串中的转义字符
            // 例如将 \" 转换为 "，将 \\n 转换为空格等
            return unescapeJsonString(m.group(1));
        }

        // 如果没有匹配到任何内容，返回 null 表示提取失败
        return null;
    }

    /**
     * 清洗改写后的查询文本，去除各种噪声和不必要的字符
     *
     * 清洗步骤：
     * 1. 检查输入是否为有效文本
     * 2. 将所有换行符（\r\n）替换为空格，统一文本格式
     * 3. 将多个连续空白字符合并为单个空格
     * 4. 去除首尾空白字符
     * 5. 移除可能的前缀标记（如 "改写:" 或 "rewrittenQuery:"）
     * 6. 移除尾部的标点符号（中英文逗号、句号、感叹号、问号、分号、冒号等）
     * 7. 再次去除首尾空白并返回结果
     *
     * @param value 待清洗的改写文本
     * @return 清洗后的文本，如果清洗后为空则返回 null
     */
    private String cleanup(String value) {
        // 校验输入是否为有效文本
        if (!StringUtils.hasText(value)) {
            return null;
        }

        // 链式调用进行多步清洗：
        // replaceAll("[\\r\\n]+", " ") - 将一个或多个换行符替换为空格
        // replaceAll("\\s+", " ") - 将一个或多个空白字符（包括空格、制表符等）替换为单个空格
        // trim() - 去除首尾空白字符
        String s = value
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 清洗后再次检查是否为有效文本，避免后续处理空字符串
        if (!StringUtils.hasText(s)) {
            return null;
        }

        // 移除可能的前缀标记，这些标记可能是 LLM 输出时的额外说明
        // ^(改写|rewrittenQuery)[:：]\\s* 匹配以 "改写" 或 "rewrittenQuery" 开头，
        // 后跟中文或英文冒号，以及可选空白的模式
        // replaceAll() 将匹配到的前缀替换为空字符串
        s = s.replaceAll("^(改写|rewrittenQuery)[:：]\\s*", "").trim();

        // 移除尾部的标点符号，这些标点对检索没有帮助反而可能干扰相似度计算
        // [，。！？；,.!?;:：]+$ 匹配末尾的一个或多个中英文标点符号
        // $ 表示字符串结尾，+ 表示一个或多个
        s = s.replaceAll("[，。！？；,.!?;:：]+$", "").trim();

        // 返回最终清洗后的文本
        return s;
    }

    /**
     * 标准化查询文本，统一格式以便后续处理
     *
     * 标准化操作：
     * 1. 将所有换行符（\r\n）替换为空格，消除多行文本的影响
     * 2. 将多个连续空白字符合并为单个空格，减少冗余
     * 3. 去除首尾空白字符，得到干净的文本
     *
     * @param question 原始查询文本
     * @return 标准化后的查询文本
     */
    private String normalize(String question) {
        // 链式调用进行标准化处理：
        // replaceAll("[\\r\\n]+", " ") - 将一个或多个换行符替换为空格
        // replaceAll("\\s+", " ") - 将一个或多个空白字符替换为单个空格
        // trim() - 去除首尾空白字符
        return question
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 强制限制文本的最大长度，防止过长的查询影响检索性能
     *
     * 处理逻辑：
     * 1. 检查输入是否为有效文本
     * 2. 确保最大长度不小于 30，避免截断过短导致信息丢失
     * 3. 去除首尾空白字符
     * 4. 如果文本长度不超过最大值，直接返回
     * 5. 如果超过最大值，截取前 max 个字符并去除尾部空白
     *
     * @param topic 待限制的文本
     * @param maxLen 配置的最大长度
     * @return 限制长度后的文本，如果输入为空则返回 null
     */
    private String enforceMaxLength(String topic, int maxLen) {
        // 校验输入是否为有效文本
        if (!StringUtils.hasText(topic)) {
            return null;
        }

        // 确保最大长度至少为 30，防止配置值过小导致有用信息被截断
        // Math.max() 取配置值和 30 中的较大者
        int max = Math.max(30, maxLen);

        // 去除首尾空白字符，确保长度计算的准确性
        String s = topic.trim();

        // 三元运算符：如果文本长度不超过最大值，直接返回；否则截取前 max 个字符
        // substring(0, max) 截取从索引 0 到 max（不包含 max）的子字符串
        // 再次 trim() 确保截断后不会留下尾部空白
        return s.length() <= max ? s : s.substring(0, max).trim();
    }

    /**
     * 对 JSON 字符串进行最小化的转义反转处理
     *
     * 为什么需要这个方法：
     * LLM 返回的 JSON 字符串中，特殊字符会被转义（如 \" 表示双引号，\\n 表示换行）
     * 我们需要将这些转义序列还原为原始字符，以便得到可读的文本
     *
     * 处理的转义序列：
     * - \\n → 空格（换行符在查询中没有意义，转为空格）
     * - \\r → 空格（回车符同样转为空格）
     * - \\t → 空格（制表符转为空格）
     * - \\" → "（反转义双引号）
     * - \\\\ → \（反转义反斜杠）
     *
     * 注意：这是一个最小化的实现，只处理常见的转义序列
     * 如果需要完整的 JSON 解析，应该使用专业的 JSON 库（如 Jackson、Gson）
     *
     * @param s 包含转义序列的 JSON 字符串
     * @return 反转义后的字符串，如果输入为 null 则返回 null
     */
    private String unescapeJsonString(String s) {
        // 空值检查，避免对 null 进行操作导致 NullPointerException
        if (s == null) return null;

        // 最小反转义：够用就行（避免引入额外 JSON 依赖）
        // 链式调用 replace() 依次处理各种转义序列
        // 注意：replace() 是字符串替换，不是正则替换，性能较好
        return s.replace("\\n", " ")      // 将转义的换行符替换为空格
                .replace("\\r", " ")      // 将转义的回车符替换为空格
                .replace("\\t", " ")      // 将转义的制表符替换为空格
                .replace("\\\"", "\"")    // 将转义的双引号还原为普通双引号
                .replace("\\\\", "\\");   // 将转义的反斜杠还原为普通反斜杠
    }

    /**
     * Record 类型：封装查询改写的结果数据
     *
     * Record 是 Java 16 引入的特性，专门用于不可变数据载体
     * 编译器会自动生成：
     * - 私有 final 字段：originalQuery, rewrittenQuery, rawJson
     * - 公共构造函数：接受三个参数
     * - 访问器方法：originalQuery(), rewrittenQuery(), rawJson()（注意不是 getter）
     * - equals()：基于所有字段的值比较
     * - hashCode()：基于所有字段计算哈希值
     * - toString()：自动生成格式化的字符串表示
     *
     * 字段说明：
     * - originalQuery：标准化后的原始查询文本
     * - rewrittenQuery：经过 LLM 改写并清洗后的查询文本
     * - rawJson：LLM 返回的原始 JSON 响应（用于调试和日志记录）
     *
     * 使用示例：
     * RewriteResult result = new RewriteResult("原文", "改写文", "{\"rewrittenQuery\":\"改写文\"}");
     * String original = result.originalQuery();  // 访问原始查询
     * String rewritten = result.rewrittenQuery(); // 访问改写后的查询
     */
    public record RewriteResult(String originalQuery, String rewrittenQuery, String rawJson) {
    }
}
