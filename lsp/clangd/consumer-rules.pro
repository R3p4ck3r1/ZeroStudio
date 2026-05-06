# ============================================================================
# lsp4j / JSON-RPC — Gson 反射序列化必须保留类名、字段名和接口方法名
# ============================================================================
# lsp4j 内部使用 Gson 将 JSON-RPC 消息反序列化为 Java 模型类。
# Gson 默认行为：fieldName == jsonKey。R8 重命名字段后匹配失败，
# 导致反序列化结果类型错误 → ClassCastException（无消息）。
#
# 关键：必须用 -keep class（而非 -keepclassmembers）阻止 R8 删除类。
# R8 会将仅通过 Gson 反射实例化的类（如 InitializeResult、ServerCapabilities）
# 判定为"死代码"并删除/内联。代码中直接 new 的类（如 InitializeParams）则不受影响。
# -keepclassmembers 只保留字段名，不阻止类被删除。
#
# EitherTypeAdapter.PropertyChecker 也依赖字段名判断 Either<A,B> 分支；
# EndpointProxy 通过 @JsonRequest/@JsonNotification 注解路由 JSON-RPC 方法。

# (1) 模型类：保留类名 + 字段名（Gson 反射实例化 + 字段名 == JSON key）
-keep class org.eclipse.lsp4j.** {
    <fields>;
}

# (2) 枚举：保留常量名（EnumTypeAdapter 按名称序列化）+ values/valueOf
-keepclassmembers enum org.eclipse.lsp4j.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# (3) 服务接口：保留方法名和签名（动态代理 + @JsonRequest 方法路由）
-keep interface org.eclipse.lsp4j.services.** { *; }

# (4) JSON-RPC 框架：TypeAdapter、EndpointProxy、MessageJsonHandler 等使用反射
-keep class org.eclipse.lsp4j.jsonrpc.** { *; }

# (5) LSP Launcher：createClientLauncher 通过反射构建 proxy
-keep class org.eclipse.lsp4j.launch.** { *; }
