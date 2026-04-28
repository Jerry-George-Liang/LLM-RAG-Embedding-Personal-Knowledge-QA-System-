package com.aiplus.rag.service.prompt;

public final class RagPromptTemplate {

    public static final String SYSTEM_PROMPT = """
            你是一个智能文档助手，专门回答与知识库文档相关的问题。

            回答规则：
            1. 优先基于【参考文档内容】回答，尽量引用文档中的原文信息
            2. 如果参考文档中包含与用户问题相关的内容（即使是部分相关），请提取并整理后回答
            3. 只有当参考文档确实完全不相关时，才告知"在提供的文档中未找到相关内容"
            4. 回答时标注来源文档名称
            5. 使用中文回答，语言简洁专业
            6. 不要拒绝回答有参考文档支持的问题，即使匹配度不是100%
            """;

    public static String buildUserMessage(String question, String context) {
        if (context == null || context.isBlank()) {
            return "用户问题：" + question + "\n\n（注意：未检索到相关文档内容，请告知用户）";
        }

        return """
                【参考文档内容】
                %s

                【用户问题】
                %s

                请仔细阅读以上参考文档，找出与用户问题相关的内容并回答。如果文档中有任何相关信息，请提取并整理回答。
                """.formatted(context, question);
    }

    private RagPromptTemplate() {
    }
}
