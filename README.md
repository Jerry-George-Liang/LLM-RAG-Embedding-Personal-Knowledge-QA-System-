# LLM-RAG-Embedding-Personal-Knowledge-QA-System
AI+1/
├── .gitignore                    # Git 忽略规则
├── PROJECT_STRUCTURE.md          # 项目文档
├── README.md                     # GitHub 自动生成的说明
├── backend/                      # Spring Boot 后端 (Java 21)
│   ├── pom.xml                   # Maven 配置
│   ├── start.bat                 # 启动脚本
│   └── src/main/java/com/aiplus/rag/
│       ├── controller/           # 控制器层 (3个)
│       ├── service/              # 业务逻辑层 (6个)
│       ├── config/               # 配置类 (5个)
│       ├── model/                # 数据模型 (7个)
│       ├── parser/               # 文件解析器 (6个)
│       └── exception/            # 异常处理 (3个)
└── frontend/                     # Vue 3 前端
    ├── package.json              # 依赖配置
    ├── vite.config.ts            # Vite + 代理配置
    └── src/
        ├── views/                # 页面视图
        ├── components/chat/      # 聊天组件 (4个)
        ├── components/upload/    # 上传组件 (2个)
        ├── stores/               # Pinia 状态管理 (4个)
        ├── api/                  # HTTP 封装
        ├── utils/                # SSE 工具函数
        └── types/                # TypeScript 类型定义
