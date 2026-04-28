# Spring Boot 文件解析器设计（策略模式）

> 本文详细介绍如何使用策略模式和工厂模式在 Spring Boot 中设计一个可扩展的文件解析框架，支持 PDF、Word、Text 等多种文件格式。

## 目录

1. [设计模式概述](#设计模式概述)
2. [策略模式详解](#策略模式详解)
3. [文件解析器实现](#文件解析器实现)
4. [工厂模式应用](#工厂模式应用)
5. [完整代码示例](#完整代码示例)

***

## 设计模式概述 {#设计模式概述}

### 1.1 为什么需要设计模式？

在文件解析场景中，我们可能需要：

- 解析 PDF 文档
- 解析 Word 文档（.doc/.docx）
- 解析纯文本文件
- 未来可能支持更多格式（Excel、HTML 等）

如果使用 if-else 写法：

```java
// ❌ 不推荐：大量 if-else，难以维护
public String parse(String fileType, Path filePath) {
    if ("pdf".equals(fileType)) {
        // PDF 解析逻辑
    } else if ("docx".equals(fileType)) {
        // Word 解析逻辑
    } else if ("txt".equals(fileType)) {
        // 文本解析逻辑
    }
    // ... 更多类型
}
```

### 1.2 更好的方案：策略 + 工厂

```
┌─────────────────────────────────────────────────────────┐
│                    FileParserFactory                     │
│                        (工厂)                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   ┌─────────────┐    ┌─────────────┐                   │
│   │PdfFileParser│    │WordFileParser│                   │
│   │  (策略A)    │    │   (策略B)   │    ...            │
│   └──────┬──────┘    └──────┬──────┘                   │
│          │                   │                          │
│          ▼                   ▼                          │
│   ┌──────────────────────────────────────────────┐     │
│   │              FileParser (接口)                │     │
│   │  + parse(Path): DocumentParseResult          │     │
│   │  + getSupportedExtensions(): String[]        │     │
│   └──────────────────────────────────────────────┘     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

***

## 2. 策略模式详解

### 2.1 什么是策略模式？

**策略模式（Strategy Pattern）**：定义一系列算法，将每个算法封装起来，使它们可以互换。

### 2.2 策略模式结构

```
┌─────────────────────┐
│     Context         │
│   (上下文环境)       │
├─────────────────────┤
│ - strategy: Strategy│
├─────────────────────┤
│ + setStrategy()     │
│ + execute()         │
└─────────┬───────────┘
          │
          │ 使用
          ▼
┌─────────────────────┐     ┌─────────────────────┐
│     Strategy        │     │    ConcreteA        │
│   (策略接口)         │◀────│   (具体策略A)       │
├─────────────────────┤     ├─────────────────────┤
│ + algorithm()       │     │ + algorithm()       │
└─────────────────────┘     └─────────────────────┘
```

### 2.3 策略模式优势

1. **开闭原则**：新增文件类型只需添加新的解析器，无需修改现有代码
2. **单一职责**：每种文件格式的解析逻辑独立管理
3. **消除条件分支**：用多态替代 if-else
4. **可测试性**：每个解析器可以独立单元测试

***

## 文件解析器实现 {#文件解析器实现}

### 3.1 定义解析器接口

```java
/**
 * 文件解析器接口
 * 
 * 所有文件解析器必须实现此接口
 */
public interface FileParser {

    /**
     * 解析文件
     *
     * @param filePath 文件路径
     * @return 解析结果
     * @throws Exception 解析异常
     */
    DocumentParseResult parse(Path filePath) throws Exception;

    /**
     * 获取支持的文件扩展名
     *
     * @return 文件扩展名数组，如 ["pdf", "txt"]
     */
    String[] getSupportedExtensions();
}
```

### 3.2 统一解析结果模型

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseResult {

    /** 文档 ID */
    private String documentId;

    /** 文件名 */
    private String filename;

    /** 提取的文本内容 */
    private String content;

    /** 分割后的文本块 */
    private List<TextChunk> chunks;

    /** 附加元数据 */
    private Map<String, Object> metadata;

    /** 解析是否成功 */
    @Builder.Default
    private boolean success = true;

    /** 错误信息 */
    private String errorMessage;

    @Data
    @Builder
    public static class TextChunk {
        private String id;
        private String content;
        private int startIndex;
        private int endIndex;
        private Integer pageNumber;
    }
}
```

### 3.3 PDF 解析器实现

```java
/**
 * PDF 文件解析器
 * 
 * 使用 Apache PDFBox 解析 PDF 文档
 */
@Slf4j
@Component
public class PdfFileParser implements FileParser {

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        log.info("开始解析 PDF: {}", filePath);
        
        File file = filePath.toFile();
        StringBuilder content = new StringBuilder();
        Map<String, Object> metadata = new HashMap<>();

        try (PDDocument document = PDDocument.load(file)) {
            int pageCount = document.getNumberOfPages();
            metadata.put("pageCount", pageCount);
            metadata.put("fileType", "pdf");

            // 使用 PDFTextStripper 提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            
            // 按页提取
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    content.append(pageText).append("\n\n");
                }
            }

            String fullText = content.toString().trim();
            metadata.put("wordCount", countWords(fullText));

            return DocumentParseResult.builder()
                    .filename(file.getName())
                    .content(fullText)
                    .chunks(new ArrayList<>())
                    .metadata(metadata)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("PDF 解析失败", e);
            return DocumentParseResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"pdf"};
    }
}
```

### 3.4 Word 解析器实现

```java
/**
 * Word 文件解析器
 * 
 * 支持 .doc (Office 97-2003) 和 .docx (Office 2007+)
 */
@Slf4j
@Component
public class WordFileParser implements FileParser {

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        log.info("开始解析 Word: {}", filePath);
        
        File file = filePath.toFile();
        String fileName = file.getName().toLowerCase();
        StringBuilder content = new StringBuilder();
        Map<String, Object> metadata = new HashMap<>();

        try {
            if (fileName.endsWith(".docx")) {
                parseDocx(file, content, metadata);
            } else {
                parseDoc(file, content, metadata);
            }

            String fullText = content.toString().trim();
            metadata.put("wordCount", countWords(fullText));

            return DocumentParseResult.builder()
                    .filename(file.getName())
                    .content(fullText)
                    .chunks(new ArrayList<>())
                    .metadata(metadata)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Word 解析失败", e);
            return DocumentParseResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 解析 .docx (Office 2007+)
     */
    private void parseDocx(File file, StringBuilder content, 
                          Map<String, Object> metadata) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("fileType", "docx");

            for (XWPFParagraph para : document.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    content.append(text).append("\n");
                }
            }
        }
    }

    /**
     * 解析 .doc (Office 97-2003)
     */
    private void parseDoc(File file, StringBuilder content,
                         Map<String, Object> metadata) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            
            String[] paragraphs = extractor.getParagraphText();
            metadata.put("paragraphCount", paragraphs.length);
            metadata.put("fileType", "doc");

            for (String para : paragraphs) {
                if (para != null && !para.isBlank()) {
                    content.append(para).append("\n");
                }
            }
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"doc", "docx"};
    }
}
```

### 3.5 文本解析器实现

```java
/**
 * 文本文件解析器
 * 
 * 支持纯文本和 Markdown 文件
 */
@Slf4j
@Component
public class TextFileParser implements FileParser {

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        log.info("开始解析文本: {}", filePath);
        
        try {
            String content = Files.readString(filePath);
            Map<String, Object> metadata = new HashMap<>();
            
            String fileName = filePath.getFileName().toString().toLowerCase();
            String fileType = fileName.endsWith(".md") ? "markdown" : "text";
            
            metadata.put("fileType", fileType);
            metadata.put("wordCount", countWords(content));
            metadata.put("lineCount", content.split("\n").length);

            return DocumentParseResult.builder()
                    .filename(filePath.getFileName().toString())
                    .content(content)
                    .chunks(new ArrayList<>())
                    .metadata(metadata)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("文本解析失败", e);
            return DocumentParseResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"txt", "md", "log"};
    }
}
```

***

## 工厂模式应用 {#工厂模式应用}

### 4.1 什么是工厂模式？

**工厂模式（Factory Pattern）**：定义一个创建对象的接口，让子类决定实例化哪个类。

### 4.2 解析器工厂实现

```java
/**
 * 文件解析器工厂
 * 
 * 使用 Spring 自动注入所有 FileParser 实现
 * 自动维护文件类型到解析器的映射
 */
@Slf4j
@Component
public class FileParserFactory {

    /** 文件类型 -> 解析器映射 */
    private final Map<String, FileParser> parserMap = new HashMap<>();

    /** 支持的文件类型列表 */
    private final List<String> supportedTypes;

    /**
     * 构造函数 - Spring 自动注入所有 FileParser
     */
    public FileParserFactory(List<FileParser> parsers) {
        // 注册所有解析器
        for (FileParser parser : parsers) {
            String[] extensions = parser.getSupportedExtensions();
            for (String ext : extensions) {
                parserMap.put(ext.toLowerCase(), parser);
                log.info("注册解析器: {} -> {}", ext, 
                         parser.getClass().getSimpleName());
            }
        }
        
        supportedTypes = List.copyOf(parserMap.keySet());
        log.info("文件解析器初始化完成，支持: {}", supportedTypes);
    }

    /**
     * 获取解析器
     *
     * @param fileType 文件扩展名 (不含点号)
     * @return 对应的解析器，不支持返回 null
     */
    public FileParser getParser(String fileType) {
        if (fileType == null) return null;
        return parserMap.get(fileType.toLowerCase());
    }

    /**
     * 检查是否支持
     */
    public boolean isSupported(String fileType) {
        return fileType != null && parserMap.containsKey(fileType.toLowerCase());
    }

    /**
     * 获取所有支持的类型
     */
    public List<String> getSupportedTypes() {
        return supportedTypes;
    }
}
```

### 4.3 工厂使用示例

```java
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final FileParserFactory parserFactory;

    /**
     * 解析文档
     */
    public DocumentParseResult parseDocument(String documentId) {
        DocumentMetadata metadata = getDocumentMetadata(documentId);
        String fileType = metadata.getFileType();
        
        // 获取合适的解析器
        FileParser parser = parserFactory.getParser(fileType);
        
        if (parser == null) {
            throw new IllegalArgumentException(
                "不支持的文件类型: " + fileType + 
                "，支持的类型: " + parserFactory.getSupportedTypes()
            );
        }
        
        Path filePath = Paths.get(metadata.getStoragePath());
        return parser.parse(filePath);
    }
}
```

***

## 完整代码示例 {#完整代码示例}

### 5.1 整体类图

```
┌─────────────────────────────────────────────────────────────┐
│                      FileParserFactory                        │
│                        (工厂类)                               │
├─────────────────────────────────────────────────────────────┤
│ - parserMap: Map<String, FileParser>                        │
├─────────────────────────────────────────────────────────────┤
│ + getParser(fileType): FileParser                          │
│ + isSupported(fileType): boolean                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 创建
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      <<interface>>                           │
│                       FileParser                             │
├─────────────────────────────────────────────────────────────┤
│ + parse(Path): DocumentParseResult                         │
│ + getSupportedExtensions(): String[]                        │
└─────────────────────────────────────────────────────────────┘
          ▲                ▲                ▲
          │                │                │
┌─────────┴────┐   ┌───────┴────┐   ┌───────┴────────┐
│PdfFileParser │   │WordFileParser│   │TextFileParser │
│   (PDF)     │   │   (Word)    │   │   (Text)      │
├─────────────┤   ├─────────────┤   ├───────────────┤
│ + parse()  │   │ + parse()   │   │ + parse()     │
└─────────────┘   └─────────────┘   └───────────────┘
```

### 5.2 Maven 依赖

```xml
<dependencies>
    <!-- PDF 解析 -->
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>3.0.1</version>
    </dependency>
    
    <!-- Word 解析 -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.2.5</version>
    </dependency>
</dependencies>
```

### 5.3 添加新解析器（扩展）

只需创建新的解析器类，实现 `FileParser` 接口即可：

```java
/**
 * Excel 文件解析器 (示例)
 */
@Slf4j
@Component
public class ExcelFileParser implements FileParser {

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        // Excel 解析逻辑
        // ...
        return DocumentParseResult.builder().build();
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"xlsx", "xls"};
    }
}
```

> 🎉 无需修改任何现有代码！Spring 会自动注册新的解析器。

***

## 📚 设计模式总结

### 本项目使用的设计模式

| 模式       | 应用场景        | 优势            |
| -------- | ----------- | ------------- |
| **策略模式** | 文件解析器       | 消除条件分支，支持开闭原则 |
| **工厂模式** | 解析器创建       | 统一创建逻辑，隐藏实现细节 |
| **单例模式** | Spring Bean | 避免重复创建，节省资源   |
| **模板方法** | 解析结果构建      | 统一处理流程，子类实现细节 |

### 核心原则

1. **开闭原则**：对扩展开放，对修改关闭
2. **依赖倒置**：依赖抽象而非具体实现
3. **单一职责**：每个类只做一件事

***

> 💡 提示：这种设计模式组合在实际开发中非常常见，建议熟练掌握！

