package com.jianjian.ai.zksh.agent.workflow;

import com.jianjian.ai.zksh.agent.workflow.config.ChatWorkflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 顺序聊天工作流编排器
 *
 * 核心职责：
 * 1. 按照预定义的步骤顺序执行聊天工作流中的各个Agent节点
 * 2. 管理每个步骤的执行状态、超时控制和错误处理
 * 3. 维护整个工作流的执行上下文（AgenticScope），在各步骤间传递数据
 *
 * 工作流程：
 * - 从Spring容器中注入所有实现了AgentStep接口的步骤实例
 * - 根据配置动态决定哪些步骤需要执行
 * - 对每个步骤设置独立的超时时间，防止某个步骤卡死影响整体流程
 * - 支持容错模式：当某步骤失败时，可选择继续执行或中断流程
 * - 记录每个步骤的执行耗时和错误信息，便于监控和调试
 *
 * 使用场景：
 * 适用于多轮对话场景，例如：主题识别 -> 问题改写 -> 答案生成 的流水线处理
 */
@Service
public class SequentialChatWorkflowOrchestrator {

    /**
     * SLF4J日志记录器
     * 用于记录工作流执行过程中的警告、错误和关键信息
     */
    private static final Logger log = LoggerFactory.getLogger(SequentialChatWorkflowOrchestrator.class);

    /**
     * Agent步骤列表
     *
     * 通过Spring的依赖注入自动收集所有实现了AgentStep接口的Bean
     * 这些步骤会按照它们在Spring容器中的注册顺序依次执行
     *
     * 注意：步骤的执行顺序取决于Spring Bean的加载顺序，如果需要严格控制顺序，
     * 应该在各个AgentStep实现类上使用@Order注解指定优先级
     */
    @Autowired
    private List<AgentStep> steps;

    /**
     * 工作流配置属性对象
     *
     * 包含以下配置项：
     * - 是否启用整个工作流功能
     * - 每个步骤的独立开关和超时时间配置
     * - 默认超时时间
     * - 出错时是否继续执行的策略
     *
     * 通过application.yml中的配置项进行外部化配置，支持运行时动态调整
     */
    @Autowired
    private ChatWorkflowProperties properties;

    /**
     * 执行完整的聊天工作流
     *
     * 这是整个编排器的核心方法，负责协调所有Agent步骤的顺序执行。
     *
     * 执行流程详解：
     * 1. 创建初始的AgenticScope上下文对象，封装用户ID、会话ID、记忆ID和原始查询
     * 2. 检查工作流总开关，如果禁用则直接返回空的scope（快速失败）
     * 3. 遍历所有注册的Agent步骤，对每个步骤执行以下操作：
     *    a) 获取步骤类型标识
     *    b) 检查该步骤是否在配置中启用，未启用则跳过
     *    c) 记录开始时间戳，用于计算执行耗时
     *    d) 解析该步骤的超时时间配置
     *    e) 使用CompletableFuture异步执行步骤，并附加超时控制
     *    f) 捕获超时异常和其他异常，转换为统一的StepResult.fail结果
     *    g) 计算执行耗时并存入scope的延迟统计地图
     *    h) 如果步骤执行失败：
     *       - 记录错误信息到scope的错误地图
     *       - 打印警告日志
     *       - 根据配置决定是否中断后续步骤的执行
     * 4. 返回填充了所有执行结果的AgenticScope对象
     *
     * @param userId 用户唯一标识，用于追踪是哪个用户发起的请求
     * @param sessionId 会话ID，标识当前对话会话，用于关联同一会话的多轮消息
     * @param memoryId 记忆ID，用于检索历史对话记忆，提供上下文信息
     * @param originalQuery 用户的原始查询文本，即用户输入的问题或指令
     * @return AgenticScope 包含完整执行结果的上下文对象，包括各步骤的输出、耗时和错误信息
     */
    public AgenticScope run(Long userId, Long sessionId, String memoryId, String originalQuery) {
        // 创建新的工作流执行上下文，初始化用户、会话、记忆和查询信息
        // 这个对象会在各个步骤之间传递，作为数据共享的载体
        AgenticScope scope = new AgenticScope(userId, sessionId, memoryId, originalQuery);

        // 检查工作流全局开关
        // 如果配置中禁用了工作流功能，直接返回空的scope，不执行任何步骤
        // 这种设计允许在不停服的情况下动态关闭复杂的工作流功能
        if (!properties.isEnabled()) {
            return scope;
        }

        // 遍历所有注册的Agent步骤，按顺序执行
        // steps列表由Spring自动注入，包含所有实现了AgentStep接口的Bean
        for (AgentStep step : steps) {
            // 获取当前步骤的类型枚举标识
            // 类型标识用于查找对应的配置项（是否启用、超时时间等）
            WorkflowStepType stepType = step.type();

            // 检查当前步骤是否在配置中启用
            // 如果配置中将该步骤设置为禁用，则跳过执行，直接进入下一个步骤
            // 这允许灵活地开启/关闭特定步骤，而不需要修改代码
            if (!isStepEnabled(stepType)) {
                continue;
            }

            // 记录步骤开始执行的时间戳（毫秒级）
            // 用于后续计算该步骤的实际执行耗时
            long begin = System.currentTimeMillis();

            // 声明步骤执行结果变量，用于存储执行成功或失败的结果
            StepResult result;

            try {
                // 解析当前步骤的超时时间配置（毫秒）
                // 不同步骤可能需要不同的超时策略，例如复杂的RAG检索可能需要更长的超时时间
                long timeoutMs = resolveTimeoutMs(stepType);

                // 使用CompletableFuture异步执行步骤，并附加超时控制
                // 详细执行逻辑：
                // 1. supplyAsync：在ForkJoinPool.commonPool()中异步执行step.execute(scope)
                // 2. orTimeout：为异步任务设置超时限制，超时后抛出TimeoutException
                // 3. exceptionally：捕获超时异常或其他异常，转换为失败的StepResult
                // 4. join：阻塞等待异步任务完成并返回结果
                //
                // 这种设计的优势：
                // - 即使step.execute内部没有实现超时控制，外层也能强制中断
                // - 异常被统一捕获并转换为StepResult，不会导致整个工作流崩溃
                // - 异步执行为未来可能的并行化优化预留了空间
                result = CompletableFuture.supplyAsync(() -> step.execute(scope))
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(e -> StepResult.fail("timeout or execution error: " + e.getMessage()))
                        .join();
            } catch (Exception e) {
                // 捕获意外的运行时异常（例如NullPointerException等）
                // 这些异常通常不应该发生，但为了健壮性仍然需要处理
                // 将异常转换为失败的StepResult，避免整个工作流中断
                result = StepResult.fail("unexpected error: " + e.getMessage());
            }

            // 计算步骤执行耗时：当前时间减去开始时间
            // 这个耗时包括AI调用、数据库查询、网络请求等所有操作的总时间
            long latency = System.currentTimeMillis() - begin;

            // 将步骤耗时存入scope的延迟统计地图
            // key是步骤类型编码，value是耗时毫秒数
            // 这些数据可以用于性能监控、慢查询告警等场景
            scope.getStepLatencyMs().put(stepType.code(), latency);

            // 检查步骤执行是否成功
            if (!result.success()) {
                // 步骤执行失败，记录错误信息到scope的错误地图
                // 这样上层调用者可以知道哪些步骤失败了，以及失败原因
                scope.getStepErrors().put(stepType.code(), result.errorMessage());

                // 打印警告日志，记录失败的步骤类型和错误信息
                // 便于运维人员排查问题和监控系统健康状态
                log.warn("workflow step failed, step={}, error={}", stepType.code(), result.errorMessage());

                // 根据配置决定是否在步骤失败时继续执行后续步骤
                // 如果配置为不继续（continueOnError=false），则立即中断工作流
                // 这种策略适用于步骤之间有强依赖关系的场景，前一步失败后后续步骤无法执行
                if (!properties.resolveContinueOnError(stepType)) {
                    break;
                }
                // 如果配置为继续执行（continueOnError=true），则忽略当前错误，继续下一个步骤
                // 这种策略适用于步骤之间相对独立的场景，某个步骤失败不影响其他步骤
            }
        }

        // 返回填充了所有执行结果的AgenticScope对象
        // 调用者可以从scope中提取：
        // - 各步骤的输出数据（存储在scope的各个字段中）
        // - 各步骤的执行耗时（通过getStepLatencyMs()获取）
        // - 各步骤的错误信息（通过getStepErrors()获取）
        return scope;
    }

    /**
     * 判断指定类型的步骤是否启用
     *
     * 从配置对象中查找对应步骤的配置项，返回其启用状态
     *
     * @param stepType 步骤类型枚举，标识要检查的步骤
     * @return boolean true表示该步骤已启用，应该执行；false表示已禁用，应该跳过
     */
    private boolean isStepEnabled(WorkflowStepType stepType) {
        // 调用配置对象的解析方法，获取该步骤的配置项
        // 然后返回配置项中的isEnabled字段
        return properties.resolveStep(stepType).isEnabled();
    }

    /**
     * 解析指定步骤的超时时间（毫秒）
     *
     * 超时时间的确定逻辑（优先级从高到低）：
     * 1. 如果步骤配置中明确设置了超时时间（timeoutMs > 0），则使用该值
     * 2. 如果步骤配置中未设置超时时间（timeoutMs <= 0），则使用全局默认超时时间
     * 3. 无论上述哪种情况，最终结果都不能低于200毫秒的最小值
     *
     * 为什么要设置最小值200ms？
     * - 防止配置错误导致超时时间过短，步骤还没来得及执行就被中断
     * - 考虑到AI调用、数据库查询等操作的基础耗时通常在几百毫秒以上
     * - 提供一个合理的安全边界，避免误杀正常执行的步骤
     *
     * @param stepType 步骤类型枚举，标识要解析超时时间的步骤
     * @return long 超时时间（毫秒），保证 >= 200
     */
    private long resolveTimeoutMs(WorkflowStepType stepType) {
        // 从配置对象中获取该步骤的超时时间配置
        // 如果配置中未设置，可能返回0或负数
        long timeoutMs = properties.resolveStep(stepType).getTimeoutMs();

        // 三元表达式逻辑详解：
        // - 如果timeoutMs > 0：使用配置的超时时间
        // - 如果timeoutMs <= 0：使用全局默认超时时间（properties.getDefaultTimeoutMs()）
        // - Math.max(200, ...)：确保最终结果不小于200毫秒
        return Math.max(200, timeoutMs > 0 ? timeoutMs : properties.getDefaultTimeoutMs());
    }
}
